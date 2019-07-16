package nl.zakarias.constellation.edgeinference.modelServing;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * An API allowing the user to interface with the tensorflow model server, in order to make predictions.
 */
public class API {

    /**
     * Inner class used for automatic JSON <-> String conversion and access
     */
    public static class Content{
        String signature_name;
        int[][] instances;

        private Content(String signature_string, byte[][] image){
            this.signature_name = signature_string;

            instances = new int[image.length][image[0].length];

            for (int i=0; i<image.length; i++){
                for(int x=0; x<image[i].length; x++){
                    instances[i][x] = image[i][x] & 0xff; // Convert to int
                }
            }
        }
    }


    /**
     * @param con Connection to tensorflow model serving
     * @param code Response code of the request
     *
     * @return A String containing the result of the response
     * @throws IOException If something goes wrong with the connection to the server
     */
    private static String responseError(HttpURLConnection con, int code) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder result = new StringBuilder();

        result.append(String.format("Response code %d \n", code));

        while ((inputLine = in.readLine()) != null) {
            result.append(inputLine);
        }
        in.close();

        return result.toString();
    }

    /**
     * Make a prediction using tensorflow serving and the given model. Any number of images can be supplied as a batch,
     * with the first dimension representing each individual image. For example,
     * batch size 1 would correspond to the following image: byte[1][784] image.
     *
     * @param port Port number on which the tensorflow model server is listening
     * @param modelName Model name
     * @param version Model version number
     * @param image A batch of images to classify
     *
     * @return A String containing the result of the response.
     * @throws IOException If something goes wrong with the connection to the server
     */
    public static String predict(int port, String modelName, int version, byte[][] image, String signatureString) throws IOException {
        URL url = new URL("http://localhost:" + port + "/v1/models/" + modelName + ":predict");
        if (version > 0){
            url = new URL("http://localhost:" + port + "/v1/models/" + modelName + "/versions/" + version + ":predict");
        }
        Content data = new Content(signatureString, image);
        Gson gson = new Gson();

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type","application/json");

        connection.setDoOutput(true);
        connection.setDoInput(true);
        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(gson.toJson(data));
        wr.flush();
        wr.close();

        int responseCode = connection.getResponseCode();

        if (!(responseCode == 200 || responseCode == 202)) {
            throw new Error(responseError(connection, responseCode));
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder result = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            result.append(inputLine);
        }
        in.close();

        return result.toString();
    }

    /**
     * Get the status of a certain model running on the tensorflow model serving
     *
     * @param port Port number on which the tensorflow model server is listening
     * @param modelName Model name
     * @param version Model version number
     *
     * @return Status of the model
     * @throws IOException If something goes wrong with the connection to the server
     */
    public static String getStatus(int port, String modelName, int version) throws IOException {
        StringBuilder result = new StringBuilder();

        URL url = new URL("http://localhost:" + port + "/v1/models/" + modelName);
        if (version > 0){
            url = new URL("http://localhost:" + port + "/v1/models/" + modelName + "/versions/" + version);
        }
        URLConnection connection = url.openConnection();

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null)
            result.append(inputLine);
        in.close();

        return result.toString();
    }

    /**
     * Get model metadata, containing information on model input tensor dimensions, status, output format etc
     *
     * @param port Port number on which the tensorflow model server is listening
     * @param modelName Model name
     * @param version Model version number
     *
     * @return Metadata of the model
     * @throws IOException If something goes wrong with the connection to the server
     */
    public static String getModelMetadata(int port, String modelName, int version) throws IOException {
        StringBuilder result = new StringBuilder();

        URL url = new URL("http://localhost:" + port + "/v1/models/" + modelName + "/metadata");
        if (version > 0){
            url = new URL("http://localhost:" + port + "/v1/models/" + modelName + "/versions/" + version + "/metadata");
        }
        URLConnection connection = url.openConnection();

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null)
            result.append(inputLine);
        in.close();

        return result.toString();
    }
}
