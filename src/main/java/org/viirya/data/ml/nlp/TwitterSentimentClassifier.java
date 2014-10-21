
package org.viirya.data.ml.nlp;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;

import com.github.pmerienne.trident.ml.classification.Classifier;
import com.github.pmerienne.trident.ml.classification.PAClassifier;
import com.github.pmerienne.trident.ml.core.TextInstance;
import com.github.pmerienne.trident.ml.preprocessing.TwitterTokenizer;
import com.github.pmerienne.trident.ml.testing.data.Datasets;

import com.github.pmerienne.trident.ml.nlp.TextFeaturesExtractor;
import com.github.pmerienne.trident.ml.nlp.TFIDF;

public class TwitterSentimentClassifier implements Serializable {

    private static final long serialVersionUID = 1553274753609262633L;

    protected TextFeaturesExtractor featuresExtractor;
    protected Classifier<Boolean> classifier;

    private TwitterTokenizer tokenizer = new TwitterTokenizer(2, 2);

    public TwitterSentimentClassifier(String featurePath, String classifierPath) {
        try {
            this.featuresExtractor = Builder.loadFeatureExtractor(featurePath);
            this.classifier = Builder.loadClassifier(classifierPath);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load TwitterSentimentClassifier : " + e.getMessage(), e);
        }
    }

    public Boolean classify(String text) {
        List<String> tokens = this.tokenizer.tokenize(text);
        double[] features = this.featuresExtractor.extractFeatures(tokens);
        Boolean prediction = this.classifier.classify(features);
        return prediction;
    }

    protected static class Builder {

        private final static ObjectMapper MAPPER = new ObjectMapper();

        public static TextFeaturesExtractor loadFeatureExtractor(String featurePath) throws IOException {
            File TEXT_FEATURES_EXTRACTOR_FILE = new File(featurePath);
            return MAPPER.readValue(TEXT_FEATURES_EXTRACTOR_FILE, TFIDF.class);
        }

        public static Classifier<Boolean> loadClassifier(String classifierPath) throws IOException {
            File CLASSIFIER_FILE = new File(classifierPath);
            return MAPPER.readValue(CLASSIFIER_FILE, PAClassifier.class);
        }
    }
}

