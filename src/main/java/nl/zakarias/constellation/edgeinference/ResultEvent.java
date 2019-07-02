package nl.zakarias.constellation.edgeinference;

public class ResultEvent implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public byte[] predictions;
    public byte[] correct;
    public float[] certainty;

    public ResultEvent(byte[] correctClassification, byte[] predictions, float[] certainty) {
        this.correct = correctClassification;
        this.predictions = predictions;
        this.certainty = certainty;
    }

    public ResultEvent(byte[] predictions, float[] certainty){
        this(null, predictions, certainty);
    }
}
