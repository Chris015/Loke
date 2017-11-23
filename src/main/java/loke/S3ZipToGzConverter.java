package loke;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import loke.aws.S3Handler;
import loke.utils.ZipToGzUtility;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class S3ZipToGzConverter {
    private static final Logger log = LogManager.getLogger(S3ZipToGzConverter.class);
    private static final String ZIP_PATTERN = "^.*aws-billing-detailed-line-items-with-resources-and-tags.*.zip$";
    private S3Handler s3Handler;
    private ZipToGzUtility zipToGzUtility;

    public S3ZipToGzConverter(S3Handler s3Handler, ZipToGzUtility zipToGzUtility) {
        this.s3Handler = s3Handler;
        this.zipToGzUtility = zipToGzUtility;
    }

    public void convertZipToGz(String sourceBucket, String destinationBucket){
        File tmpDir = new File("tmp");
        if(!tmpDir.isFile()) {
            tmpDir.mkdir();
        }
        String zipFile = downloadLastModifiedIZip(sourceBucket, tmpDir.getPath());

        String gzipFile = null;
        try {
            gzipFile = zipToGzUtility.convertZipToGz(zipFile, tmpDir.getPath());
        } catch (ZipToGzUtility.ConversionErrorException e) {
            e.printStackTrace();
            System.exit(1);
        }

        log.info("GzipFile: {}", gzipFile);
        s3Handler.uploadFile(destinationBucket, gzipFile, tmpDir.getPath() + gzipFile);

        try {
            FileUtils.cleanDirectory(tmpDir);
            log.trace("Tmp dir cleaned");
        } catch (IOException e) {
            log.error("Tmp dir could not be cleaned");
            e.printStackTrace();
        }
    }

    private String downloadLastModifiedIZip(String sourceBucket, String destinationPath) {
        List<S3ObjectSummary> objectSummaries = s3Handler.getObjectSummeries(sourceBucket);

        S3ObjectSummary lastModifiedFile = s3Handler.getLastModifiedFile(objectSummaries, ZIP_PATTERN);

        s3Handler.downloadFile(lastModifiedFile, destinationPath);
        return destinationPath + '/' + lastModifiedFile.getKey();
    }

}
