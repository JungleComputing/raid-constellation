package nl.zakarias.constellation.raid.models.tiny_yolo;

import ibis.constellation.AbstractContext;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.NoSuitableExecutorException;
import nl.zakarias.constellation.raid.configuration.Configuration;
import nl.zakarias.constellation.raid.models.ModelInterface;
import nl.zakarias.constellation.raid.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


public class TinyYolo implements ModelInterface {
    private static Logger logger = LoggerFactory.getLogger(TinyYolo.class);

    static public String modelName = Configuration.ModelName.TINY_YOLO.toString().toLowerCase(); // Matches tensorflow_serving
    static String signatureString = "predict";  // Matches tensorflow_serving

    private static int yoloImgRowLen = 608;
    private static int yoloImgColLen = 608;
    private static int yoloRGBColors = 3;

    private int batchSize = 1;
    private int batchCount = Configuration.BATCH_COUNT;
    private int timeInterval = Configuration.TIME_INTERVAL;
    private boolean endless = Configuration.ENDLESS;

    /** Fit the image to the yolo TF model input tensor dimensions by adding padding if necessary and otherwise
     * cropping. Padding is added on the right side and the bottom.
     *
     * @param image The image to fit
     * @return The fitted image
     */
    private BufferedImage fitImageToYolo(BufferedImage image){


        BufferedImage newImage = new BufferedImage(yoloImgRowLen, yoloImgColLen, image.getType());
        Graphics g = newImage.getGraphics();

        g.setColor(Color.white);
        g.fillRect(0,0, yoloImgRowLen, yoloImgColLen);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        image = newImage;

        return image;
    }

    private byte[][][] convertBufferedImageTo3DByteArray(BufferedImage image){
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        int pixelLength = 3; // RGB

        byte[][][] result = new byte[yoloImgRowLen][yoloImgColLen][pixelLength];

        // Check if image has alpha channel
        if (image.getAlphaRaster() != null){
            // Copy pixel values into the byte array
            for (int pixel = 0, row = 0, col = 0; pixel + 3 < pixels.length; pixel += pixelLength+1) {
                // Ignore alpha = pixels[pixel]
                result[row][col][0] = pixels[pixel+1];
                result[row][col][1] = pixels[pixel+2];
                result[row][col][2] = pixels[pixel+3];

                col++;
                if (col == yoloImgRowLen) {
                    col = 0;
                    row++;
                }
            }

            return result;
        }

        // No Alpha
        // Copy pixel values into the byte array
        for (int pixel = 0, row = 0, col = 0; pixel + 2 < pixels.length; pixel += pixelLength) {
            result[row][col][0] = pixels[pixel];
            result[row][col][1] = pixels[pixel+1];
            result[row][col][2] = pixels[pixel+2];

            col++;
            if (col == yoloImgRowLen) {
                col = 0;
                row++;
            }
        }

        return result;
    }

    private void runYolo(Constellation constellation, ActivityIdentifier aid, String sourceDir, AbstractContext contexts) throws IOException, NoSuitableExecutorException {
        if (logger.isDebugEnabled()) {
            logger.debug("Checking source path");
        }
        Object[] files = Files.list(Paths.get(sourceDir)).filter(Files::isRegularFile).toArray();

        if (logger.isDebugEnabled()) {
            logger.debug("Starting to submit images in batches of " + batchSize);
        }

        int pos = 0;
        while (pos < files.length){
            byte[][][][] images = new byte[batchSize][yoloImgRowLen][yoloImgColLen][yoloRGBColors];
            int min = Math.min(pos + batchSize, files.length);

            for (int i=pos; i<min; i++){
                BufferedImage image = fitImageToYolo(Utils.readJPG(files[i].toString(), yoloImgRowLen, yoloImgColLen));

                images[i-pos] = convertBufferedImageTo3DByteArray(image);
            }

            // Generate imageIdentifiers in order to link back the result to the image CURRENTLY DISCARDED UPON METHOD EXIT
            int[] imageIdentifiers = new int[images.length];
            // Create imageIdentifiers
            for(int i=0; i<batchSize; i++){
                imageIdentifiers[i] = Utils.imageIdentifier(images[i]);
            }

            System.out.println(files[pos] + " - " + imageIdentifiers[0]);

            // Generate activity
            TinyYoloActivity activity = new TinyYoloActivity(constellation.identifier().toString(), contexts, true, false, images, aid, imageIdentifiers);

            // submit activity
            if (logger.isDebugEnabled()) {
                logger.debug("Submitting YoloActivity with contexts " + contexts.toString());
            }
            constellation.submit(activity);

            pos = min;
            try {
                Thread.sleep(this.timeInterval);
            } catch (InterruptedException e) {
                logger.error("Failed to sleep between submitting batches");
            }
            if (pos == this.batchCount && !Configuration.ENDLESS){
                break;
            }
        }

    }

    @Override
    public void run(Constellation constellation, ActivityIdentifier targetActivityIdentifier, String sourceDir, AbstractContext contexts, int batchSize, int timeInterval, int batchCount, boolean endless) throws IOException, NoSuitableExecutorException {
        this.batchSize = batchSize;
        this.timeInterval = timeInterval;
        this.batchCount = batchCount;
        this.endless = endless;
        runYolo(constellation, targetActivityIdentifier, sourceDir, contexts);
    }
}