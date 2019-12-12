package nl.zakarias.constellation.raid.models.mnist_cnn;

import ibis.constellation.AbstractContext;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.NoSuitableExecutorException;
import nl.zakarias.constellation.raid.configuration.Configuration;
import nl.zakarias.constellation.raid.models.ModelInterface;
import nl.zakarias.constellation.raid.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MnistCnn implements ModelInterface {
    private static Logger logger = LoggerFactory.getLogger(MnistCnn.class);

    static String modelName = Configuration.ModelName.MNIST_CNN.toString().toLowerCase();  // Matches tensorflow_serving
    static String signatureString = "predict";  // Matches tensorflow_serving

    private static final int NUMBER_OF_MNIST_IMAGES = 10000; // Must be even 10000

    private int batchSize;
    private int batchCount = Configuration.BATCH_COUNT;
    private int timeInterval = Configuration.TIME_INTERVAL;
    private boolean endless = Configuration.ENDLESS;

    private void sendMnistImageBatch(byte[][][][] images, byte[] targets, Constellation constellation, ActivityIdentifier aid, AbstractContext contexts) throws IOException, NoSuitableExecutorException {
        // Generate imageIdentifiers in order to link back the result to the image CURRENTLY DISCARDED UPON METHOD EXIT
        int[] imageIdentifiers = new int[images.length];
        // Create imageIdentifiers
        for(int i=0; i<imageIdentifiers.length; i++){
            imageIdentifiers[i] = Utils.imageIdentifier(images[i]);
        }

        // Generate activity
        MnistCnnActivity activity = new MnistCnnActivity(constellation.identifier().toString(), contexts, true, false, images, targets, aid, imageIdentifiers);

        // submit activity
        if (logger.isDebugEnabled()) {
            logger.debug("Submitting MnistActivity with contexts " + contexts.toString());
        }
        constellation.submit(activity);
    }

    private void runMnist(Constellation constellation, ActivityIdentifier target, String sourceDir, AbstractContext contexts) throws IOException, NoSuitableExecutorException {
        if (logger.isDebugEnabled()) {
            logger.debug("Reading MNIST image and label file...");
        }

        byte[][][][] images = Utils.readMnist_3D(sourceDir + "/t10k-images-idx3-ubyte");
        byte[] targets = Utils.readLabelsMnist(sourceDir + "/t10k-labels-idx1-ubyte");
        if (logger.isDebugEnabled()) {
            logger.debug("Done importing images");
        }

        int counter = 0;
        while(true){
            // Check if we are approaching the Java heap memory limitations, in that case stop uploading until
            // We have more memory available
            if (Runtime.getRuntime().totalMemory() == Runtime.getRuntime().maxMemory() && Runtime.getRuntime().freeMemory() < (Configuration.HEAP_MEMORY_THRESHOLD*1000)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Memory threshold reached, Activity submission blocked. Avail memory: " + Runtime.getRuntime().freeMemory()/1000 + "KB");
                }

                try {
                    Thread.sleep(this.timeInterval);
                } catch (InterruptedException e) {
                    logger.error("Failed to sleep when approaching Java heap memory limitation");
                }

                // Start over as other nodes might have stolen some Activities from this node
                continue;
            }

            for (int i = 0; i < this.batchCount; i += batchSize) {
                byte[][][][] imageBatch = new byte[batchSize][images[i].length][images[i][0].length][images[i][0][0].length];
                byte[] targetBatch = new byte[batchSize];

                for (int x = 0; x < batchSize; x++) {
                    imageBatch[x] = images[i + x];
                    targetBatch[x] = targets[i + x];
                }

                sendMnistImageBatch(imageBatch, targetBatch, constellation, target, contexts);

                try {
                    Thread.sleep(this.timeInterval);
                } catch (InterruptedException e) {
                    logger.error("Failed to sleep between submitting batches");
                }

                counter++;
                if (counter == this.batchCount && !endless){
                    return;
                }
            }
        }
    }

    @Override
    public void run(Constellation constellation, ActivityIdentifier targetActivityIdentifier, String sourceDir, AbstractContext contexts, int batchSize, int timeInterval, int batchCount, boolean endless) throws IOException, NoSuitableExecutorException {
        this.batchSize = batchSize;
        this.timeInterval = timeInterval;
        this.batchCount = batchCount;
        this.endless = endless;
        runMnist(constellation, targetActivityIdentifier, sourceDir, contexts);
    }
}
