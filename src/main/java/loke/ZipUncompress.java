package loke;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class ZipUncompress {
    private static final Logger log = LogManager.getLogger(ZipUncompress.class);
    private S3Handler s3Handler;

    public ZipUncompress(S3Handler s3Handler) {
        this.s3Handler = s3Handler;
    }

    // download latest zip file
    private void downloadLastModifiedItem() {
        String keyPatternRegExp = "^.*aws-billing-detailed-line-items-with-resources-and-tags.*.zip$";
        List<S3ObjectSummary> objectSummaries = s3Handler.getObjectSummeries("wsqa-billingreports");
        S3ObjectSummary lastModifiedFile = s3Handler.getLastModifiedFile(objectSummaries, keyPatternRegExp);

        String destination = "/Users/praktikant/Development/Repos/loke";
        s3Handler.downloadFile(lastModifiedFile, destination);

    }




    // uncompress

    // upload to s3 bucket
}
