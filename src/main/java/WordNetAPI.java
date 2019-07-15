import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * API Singleton Class for finding synonyms of words using WordNet
 * For usage, create an instance using WordNetAPI.getInstance(), and then call getSynonyms with the word and its POS
 */
public class WordNetAPI {
    private static WordNetAPI _instance;
    private File file;
    private IDictionary dict;

    /**
     * Private constructor for the WordNetAPI class
     * Loads the dictionary file from disk
     */
    private WordNetAPI() {
        file = new File("data/dict/");
        dict = new Dictionary(file);
        try {
            dict.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get an instance of the class
     *
     * @return an instance of WordNetAPI class
     */
    public static WordNetAPI getInstance() {
        if (_instance == null) {
            _instance = new WordNetAPI();
        }
        return _instance;
    }

    /**
     * Get the synonyms of a word.
     * The words needs to be supported with its POS.
     * Possible Part of speech strings can be found <a href="https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html">here.</a>
     *
     * @param word the word to find synonyms for
     * @param pos  part of speech of the word
     * @return list of synonyms of the given word
     */
     ArrayList<String> getSynonyms(String word, String pos) {
        // construct the URL to the WordNet dictionary directory
        // construct the dictionary object and open it
        ArrayList<String> words = new ArrayList<>();
        try {
            HashSet<String> uniqueWords = new HashSet<>();
            //finding its part of speech from the abbreviation
            POS t;
            if (pos.startsWith("NN")) {
                t = POS.NOUN;
            } else if (pos.startsWith("VB")) {
                t = POS.VERB;
            } else if (pos.startsWith("JJ")) {
                t = POS.ADJECTIVE;
            } else if (pos.startsWith("RB")) {
                t = POS.ADVERB;
            } else {
                return new ArrayList<>();
            }
            //get all the potential meaning of a word
            IIndexWord idxWord = dict.getIndexWord(word, t);

            for (int i = 0; i < idxWord.getWordIDs().size(); i++) {
                IWordID wordID = idxWord.getWordIDs().get(i); // ist meaning
                //each potential word has a synset root
                edu.mit.jwi.item.IWord iword = dict.getWord(wordID);
                ISynset synset = iword.getSynset();
                //go through all the word that map to this synset
                List<IWord> words_syn = synset.getWords();
                for (IWord word_syn : words_syn) {
                    String lem = word_syn.getLemma();
                    if (!uniqueWords.contains(lem)) {
                        uniqueWords.add(lem);
                        words.add(lem);
                    }
                }
            }
            return words;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
