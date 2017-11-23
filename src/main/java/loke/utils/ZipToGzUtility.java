package loke.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipToGzUtility {
    private static final Logger logger = LogManager.getLogger(ZipToGzUtility.class);

    public String convertZipToGz(String zipFile, String destinationPath) throws ConversionErrorException {
        try {
            logger.info("Unzipping file: {}", zipFile);
            File unzippedFile = unzip(zipFile, destinationPath);

            logger.info("GZipping file: {}", unzippedFile);
            String gzipFile = gzipIt(unzippedFile);
            logger.info("Done!");
            return gzipFile;

        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new ConversionErrorException("Could'nt convert to gzip. Did the zip file download correctly?");
    }

    private File unzip(String zipFilePath, String destDirectory) throws IOException {
        logger.info("Destination directory: {}", destDirectory);
        File destDir = new File(destDirectory);
        File resultFile = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipInputStream.getNextEntry();
            String filePath = destDirectory + File.separator + entry.getName();

            if (!entry.isDirectory()) {
                resultFile = extractFile(zipInputStream, filePath);
            } else {
                File dir = new File(filePath);
                dir.mkdir();
            }
            zipInputStream.closeEntry();
            logger.info("Result file: {}", resultFile);
        }
        return resultFile;
    }

    private File extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        File resultFile = new File(filePath);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(resultFile));

        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bufferedOutputStream.write(bytesIn, 0, read);
        }
        bufferedOutputStream.close();
        return resultFile;
    }

    private String gzipIt(File sourceFile) throws IOException {
        byte[] buffer = new byte[1024];
        File gzipFile = new File( sourceFile + ".gz");
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(
                new FileOutputStream(gzipFile));
        FileInputStream fileInputStream = new FileInputStream(sourceFile);
        int len;
        while ((len = fileInputStream.read(buffer)) > 0) {
            gzipOutputStream.write(buffer, 0, len);
        }
        fileInputStream.close();
        gzipOutputStream.finish();
        gzipOutputStream.close();
        return gzipFile.getName();
    }

    public class ConversionErrorException extends Exception {
        public ConversionErrorException(String message) {
            super(message);
        }
    }
}
