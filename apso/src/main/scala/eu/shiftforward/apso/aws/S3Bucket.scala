package eu.shiftforward.apso.aws

import com.amazonaws.{ AmazonClientException, AmazonServiceException, ClientConfiguration }
import com.amazonaws.auth._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.transfer.TransferManager
import com.typesafe.config.ConfigFactory

import eu.shiftforward.apso.Logging
import java.io.{ ByteArrayInputStream, File, FileOutputStream, InputStream }

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

import eu.shiftforward.apso.collection.InsistentInputStream

/**
 * A representation of an Amazon's S3 bucket. This class wraps an
 * [[com.amazonaws.services.s3.AmazonS3Client]] and provides a higher level interface for pushing
 * and pulling files to and from a bucket.
 *
 * @param bucketName the name of the bucket
 * @param credentialsFactory optional AWS credentials factory to use (since AWSCredentials are not serializable).
 *                    If the parameter is not supplied, they will be retrieved from the
 *                    [[eu.shiftforward.apso.aws.CredentialStore]].
 */
class S3Bucket(val bucketName: String,
               private val credentialsFactory: () => AWSCredentials = { () => CredentialStore.getCredentials })
    extends Logging with Serializable {

  @transient private[this] var _credentials: AWSCredentials = _

  private def credentials = {
    if (_credentials == null) _credentials = credentialsFactory()
    _credentials
  }

  private[this] lazy val config = ConfigFactory.load()

  private[this] lazy val configPrefix = "aws.s3"

  private[this] lazy val endpoint = Try(config.getString(configPrefix + ".endpoint")).getOrElse("s3.amazonaws.com")
  private[this] lazy val region = Try(config.getString(configPrefix + ".region")).getOrElse(null)

  @transient private[this] var _s3: AmazonS3Client = _

  private[this] def s3 = {
    if (_s3 == null) {
      val defaultConfig = new ClientConfiguration()
        .withTcpKeepAlive(true)
      _s3 = new AmazonS3Client(credentials, defaultConfig)
      _s3.setEndpoint(endpoint)
      if (!_s3.doesBucketExist(bucketName)) {
        _s3.createBucket(bucketName, Region.fromValue(region))
      }
    }
    _s3
  }

  @transient private[this] var _transferManager: TransferManager = _

  private[this] def transferManager = {
    if (_transferManager == null) {
      _transferManager = new TransferManager(s3)
    }
    _transferManager
  }

  private[this] def splitKey(key: String) = {
    val iExtensionPoint = key.lastIndexOf('.')
    val iSlash = key.lastIndexOf('/')
    val mainKey = if (iSlash != -1) key.substring(0, iSlash) else ""
    val extension = if (iExtensionPoint != -1) key.substring(iExtensionPoint) else ""
    val name = if (iExtensionPoint != -1) key.substring(0, iExtensionPoint) else key

    (mainKey, name, extension)
  }

  private[this] def sanitizeKey(key: String) = if (key.startsWith("./")) key.drop(2) else key

  /**
   * Returns size of the file in the location specified by `key` in the bucket. If the file doesn't
   * exist the return value is 0.
   *
   * @param key the remote pathname for the file
   * @return the size of the file in the location specified by `key` in the bucket if the exists, 0 otherwise.
   */
  def size(key: String): Long = retry {
    s3.getObjectMetadata(bucketName, key).getContentLength
  }.getOrElse(0)

  /**
   * Returns a list of objects in a bucket matching a given prefix.
   *
   * @param prefix the prefix to match
   * @return a list of objects in a bucket matching a given prefix.
   */
  def getObjectsWithMatchingPrefix(prefix: String, includeDirectories: Boolean = false): Iterator[S3ObjectSummary] = {
    log.info("Finding files matching prefix '{}'...", prefix)

    val listings = Iterator.iterate(s3.listObjects(bucketName, sanitizeKey(prefix))) { listing =>
      if (listing.isTruncated) {
        log.info("Asking for another batch of objects...")
        s3.listNextBatchOfObjects(listing)
      } else null
    }

    val objects = listings.takeWhile(_ != null).flatMap(_.getObjectSummaries)
    if (includeDirectories) objects else objects.filterNot(_.getKey.endsWith("/"))
  }

  /**
   * Returns a list of filenames and directories in a bucket matching a given prefix.
   *
   * @param prefix the prefix to match
   * @return a list of filenames in a bucket matching a given prefix.
   */
  def getFilesWithMatchingPrefix(prefix: String, includeDirectories: Boolean = false): Iterator[String] =
    getObjectsWithMatchingPrefix(prefix, includeDirectories).map(_.getKey)

  /**
   * Pushes a given local `File` to the location specified by `key` in the bucket.
   *
   * @param key the remote pathname for the file
   * @param file the local `File` to push
   * @return true if the push was successful, false otherwise.
   */
  def push(key: String, file: File): Boolean = retry {
    log.info("Pushing '{}' to 's3://{}/{}'", file.getPath, bucketName, key)
    transferManager
      .upload(new PutObjectRequest(bucketName, sanitizeKey(key), file))
      .waitForUploadResult()
  }.isDefined

  /**
   * Deletes the file in the location specified by `key` in the bucket.
   *
   * @param key the remote pathname for the file
   * @return true if the deletion was successful, false otherwise.
   */
  def delete(key: String): Boolean = retry {
    s3.deleteObject(bucketName, sanitizeKey(key))
  }.isDefined

  /**
   * Checks if the file in the location specified by `key` in the bucket exists.
   *
   * @param key the remote pathname for the file
   * @return true if the file exists, false otherwise.
   */
  def exists(key: String): Boolean = retry {
    s3.getObjectMetadata(bucketName, key)
  }.isDefined

  /**
   * Checks if the location specified by `key` is a directory.
   *
   * @param key the remote pathname to the directory
   * @return true if the path is a directory, false otherwise.
   */
  def isDirectory(key: String): Boolean = retry {
    s3.listObjects(
      new ListObjectsRequest()
        .withBucketName(bucketName)
        .withMaxKeys(2)
        .withPrefix(key)).getObjectSummaries
  }.exists { _.exists(_.getKey.startsWith(key + "/")) }

  /**
   * Sets an access control list on a given Amazon S3 object.
   *
   * @param key the remote pathname for the file
   * @param acl the `CannedAccessControlList` to be applied to the Amazon S3 object
   */
  def setAcl(key: String, acl: CannedAccessControlList) {
    log.info("Setting 's3://{}/{}' permissions to '{}'", bucketName, key, acl)
    s3.setObjectAcl(bucketName, key, acl)
  }

  /**
   * Creates an empty directory at the given `key` location
   *
   * @param key the remote pathname to the directory
   * @return  true if the directory was created successfully, false otherwise.
   */
  def createDirectory(key: String): Boolean = retry {
    log.info("Creating directory in 's3://{}/{}'", bucketName, key, null)

    val emptyContent = new ByteArrayInputStream(Array[Byte]())
    val metadata = new ObjectMetadata()
    metadata.setContentLength(0)

    s3.putObject(new PutObjectRequest(bucketName, sanitizeKey(key) + "/", emptyContent, metadata))
  }.isDefined

  /**
   * Backups a remote file with the given `key`. A backup consists in copying the supplied file to a backup folder under
   * the same bucket and folder the file is currently in.
   *
   * @param key the remote pathname to backup
   * @return true if the backup was successful, false otherwise.
   */
  def backup(key: String): Boolean = retry {
    val sanitizedKey = sanitizeKey(key)
    val (mainKey, name, extension) = splitKey(sanitizedKey)

    s3.copyObject(new CopyObjectRequest(
      bucketName,
      sanitizedKey,
      bucketName,
      mainKey + "/backup/" + name.substring(mainKey.length + 1) + extension))
  }.isDefined

  /**
   * Pulls a remote file with the given `key`, to the local storage in the pathname provided by `destination`.
   *
   * @param key the remote pathname to pull from
   * @param destination the local pathname to pull to
   * @return true if the pull was successful, false otherwise
   */
  def pull(key: String, destination: String): Boolean = retry {
    log.info("Pulling 's3://{}/{}' to '{}'", bucketName, key, destination)

    val s3Object = s3.getObject(new GetObjectRequest(bucketName, sanitizeKey(key)))
    val inputStream = s3Object.getObjectContent

    val f = new File(destination).getCanonicalFile
    f.getParentFile.mkdirs()
    val outputStream = new FileOutputStream(f)

    var read = 0
    val bytes = new Array[Byte](1024)

    read = inputStream.read(bytes)
    while (read != -1) {
      outputStream.write(bytes, 0, read)
      read = inputStream.read(bytes)
    }

    log.info("Downloaded 's3://{}/{}' to '{}'. Closing files.", bucketName, key, destination)

    inputStream.close()
    outputStream.flush()
    outputStream.close()
  }.isDefined

  def stream(key: String): InputStream = {
    log.info("Streaming 's3://{}/{}'", bucketName: Any, key: Any)
    new InsistentInputStream(
      () => s3.getObject(new GetObjectRequest(bucketName, sanitizeKey(key))).getObjectContent,
      10,
      Some(100.milliseconds))
  }

  private[this] def handler: PartialFunction[Throwable, Boolean] = {
    case ex: AmazonS3Exception => ex.getStatusCode match {
      case 404 =>
        log.error("The specified file does not exist", ex); true // no need to retry
      case 403 =>
        log.error("No permission to access the file", ex); true // no need to retry
      case _ =>
        log.error(s"""|S3 service error: ${ex.getMessage}. Extended request id: ${ex.getExtendedRequestId}
                      |Additional details: ${ex.getAdditionalDetails}""".stripMargin, ex)
        false
    }

    case ex: AmazonServiceException =>
      log.error(s"Service error: ${ex.getMessage}", ex); ex.isRetryable

    case ex: AmazonClientException if ex.getMessage ==
      "Unable to load AWS credentials from any provider in the chain" =>
      log.error("Unable to load AWS credentials", ex); true

    case ex: AmazonClientException =>
      log.error("Client error pulling file", ex); ex.isRetryable

    case ex: Exception =>
      log.error("An error occurred", ex); false
  }

  private[this] def retry[T](f: => T, tries: Int = 3, sleepTime: Int = 5000): Option[T] =
    if (tries == 0) { log.error("Max retries reached. Aborting S3 operation"); None }
    else Try(f) match {
      case Success(res) => Some(res)
      case Failure(e) if !handler(e) =>
        if (tries > 1) {
          log.warn("Error during S3 operation. Retrying in {}ms ({} more times)",
            sleepTime, tries - 1)
          Thread.sleep(sleepTime)
        }
        retry(f, tries - 1, sleepTime)

      case _ => None
    }

  override def equals(obj: Any): Boolean = obj match {
    case b: S3Bucket => b.bucketName == bucketName &&
      b.credentials.getAWSAccessKeyId == credentials.getAWSAccessKeyId &&
      b.credentials.getAWSSecretKey == credentials.getAWSSecretKey
    case _ => false
  }
}
