package nl.zakarias.constellation.edgeinference.activites;

import nl.zakarias.constellation.edgeinference.ResultEvent;
import nl.zakarias.constellation.edgeinference.models.MnistFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.constellation.AbstractContext;
import ibis.constellation.Activity;
import ibis.constellation.Constellation;
import ibis.constellation.Event;

import java.io.IOException;

public class CollectAndProcessEvents extends Activity {
    private static final Logger logger = LoggerFactory.getLogger(CollectAndProcessEvents.class);

    private static final long serialVersionUID = -538414301465754654L;

    private int count;
    private byte[] labels;

    public CollectAndProcessEvents(AbstractContext c, String sourceDir) throws IOException {
        super(c, false, true);
        count = 1;

        this.labels = MnistFileParser.readLabelFile(sourceDir + "/t10k-labels-idx3-ubyte");
    }

    @Override
    public int initialize(Constellation c) {
        logger.debug("\nCollectAndProcessEvents: initialized\n");

        String targetIdentifier = "";
        String[] identifier = identifier().toString().split(":");
        targetIdentifier += identifier[1] + ":";
        targetIdentifier += identifier[2] + ":";
        targetIdentifier += identifier[3];

        System.out.println("In order to target this activity with classifications add the following as argument " +
                "(exactly as printed) when initializing the new SOURCE: \"" + targetIdentifier + "\"\n\n");

        // Immediately start waiting for events to process
        return SUSPEND;
    }

    @Override
    public synchronized int process(Constellation c, Event e) {
        if (logger.isDebugEnabled()) {
            logger.debug("CollectAndProcessEvents: received event number " + count + " from src id " + e.getSource().toString());
            // Handle received event
            ResultEvent result = (ResultEvent) e.getData();
            if (result.correct.equals(result.classification)) {
                logger.debug(String.format("CollectAndProcessEvent: Correctly classified as %s with certainty %1.2f", result.classification, result.certainty));
            } else {
                logger.debug(String.format("CollectAndProcessEvent: Falsely classified %s as %s with certainty %1.2f", result.correct, result.classification, result.certainty));
            }
        }

        count++;
        return SUSPEND;
    }

    @Override
    public void cleanup(Constellation c) {
        // empty
    }

    @Override
    public String toString() {
        return "CollectAndProcessEvents(" + identifier() + ")";
    }

    public synchronized void waitToFinish(){
        try {
            wait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}