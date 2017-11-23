package loke;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class S3Handler {
    private static final Logger log = LogManager.getLogger(S3Handler.class);
    private AmazonS3 s3;

    public S3Handler(AmazonS3 s3) {
        this.s3 = s3;
    }

    public List<S3ObjectSummary> getObjectSummeries(String bucket) {
        log.info("Getting object summaries for {}", bucket);
        try {
            ListObjectsV2Request request = new ListObjectsV2Request()
                    .withBucketName(bucket);
            log.trace("Get object summaries complete");
            return s3.listObjectsV2(request).getObjectSummaries();
        } catch (AmazonS3Exception e) {
            log.error("The bucket: {} does not exist in region: {}", bucket);
        }
        return null;
    }

    public S3ObjectSummary getLastModifiedFile(List<S3ObjectSummary> objectSummaries, String keyPattern) {
        List<S3ObjectSummary> filteredSummeries = new ArrayList<>();
        for (S3ObjectSummary summery : objectSummaries) {
            if (summery.getKey().matches(keyPattern)) {
                filteredSummeries.add(summery);
            }
        }

        filteredSummeries.sort(Comparator.comparing(S3ObjectSummary::getLastModified));

        int lastItem = filteredSummeries.size() - 1;
        return filteredSummeries.get(lastItem);
    }

    public void downloadFile(S3ObjectSummary lastModifiedFile, String destination) {
        GetObjectRequest request = new GetObjectRequest(lastModifiedFile.getBucketName(), lastModifiedFile.getKey());

        try (S3Object object = s3.getObject(request);
             S3ObjectInputStream inputStream = object.getObjectContent()) {

            String key = object.getKey();
            String fileName = key.substring(key.lastIndexOf('/') + 1, key.length());

            Files.copy(inputStream,
                    Paths.get(destination + "/" + fileName),
                    StandardCopyOption.REPLACE_EXISTING);
            log.info("File downloaded. \nFile: {}\nDestination: {}", fileName, destination);
        } catch (IOException e) {
            log.info("Could not download file.\nError: {}", e);
        }
    }

    public void uploadFile(String bucket, String key, String file) {
        PutObjectRequest request = new PutObjectRequest(bucket, key, file);
        try {
            s3.putObject(request);
            log.info("File uploaded.\nFile: {}\nBucket: {}\nKey: {}", file, bucket, key);
        } catch (Exception e) {
            log.info("Failed to upload file: {}\nError: ", file, e.getMessage());
        }
    }
}
