import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreEntityMention;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * API Class for enriching text and annotating it with POS, Lemmatization, and Named Entities
 * The annotation is formatted as a json file similar to <a href="http://wikifier.org">Wikifier/a> annotation
 * It uses CoreNLP as a pipeline for annotation
 * <p>
 * To use it, use {@link #process(String, String)} with the id and the text string
 * <p>
 * For per-paragraph annotation, use {@link #processAsParagraphs(String, String)}, with the same parameters.
 * This split the text into paragraphs first, then annotate each paragraph independently.
 */
public class CoreNLPAPI {
    private boolean NER;
    private StanfordCoreNLP pipeline;

    /**
     * Constructor for class #CoreNLPAPI
     *
     * @param NER whether or not to generate named entity as part of the annotation
     */
    CoreNLPAPI(boolean NER) {
        this.NER = NER;
        init();
    }

    /**
     * Initiating the CoreNLP pipeline with the suitable annotators
     * The following annotators will be added:
     * - sentence splitter
     * - tokenizer
     * - pos tagger
     * - Lemmatization
     * <p>
     * If {@link #NER} is true, Named-Entity annotator will be added as well.
     */
    private void init() {
        Properties props = new Properties();
        if (NER) {
            props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");
            props.setProperty("ner.buildEntityMentions", "true");
            props.setProperty("ner.applyFineGrained", "false");
        } else {
            props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        }
        pipeline = new StanfordCoreNLP(props);
    }

    /**
     * Generate a json object containing information about a token from a given #CoreLabel object
     *
     * @param token the token to generate the information about
     * @return json object of the token
     */
    private JSONObject tokenToJson(CoreLabel token) {
        JSONObject tokenJson = new JSONObject();
        tokenJson.put("word", token.originalText());
        tokenJson.put("token", token.word());
        tokenJson.put("norm", token.lemma());
        tokenJson.put("pos", token.tag());
        tokenJson.put("ner", token.ner());
        tokenJson.put("iFrom", token.beginPosition());
        tokenJson.put("iTo", token.endPosition() - 1);
        return tokenJson;
    }

    /**
     * Get the word index of the token w.r.t original text
     *
     * @param token                    the token extracted from the text
     * @param cumulativeSumOfSentences the running sum of number of words of sentences, to be used to identify the wordIndex
     * @return the word index of the token
     */
    private int getTokenWordIdx(CoreLabel token, ArrayList<Integer> cumulativeSumOfSentences) {
        return token.index() + (token.sentIndex() > 0 ? cumulativeSumOfSentences.get(token.sentIndex()) : 0) - 1;
    }

    /**
     * Generate a json object containing information about the given named entity,
     *
     * @param mention                  the named entity mention
     * @param cumulativeSumOfSentences the running sum of number of words of sentences, to be used to identify the wordIndex
     * @return json object of the named entity
     */
    private JSONObject entityMentionToJson(CoreEntityMention mention, ArrayList<Integer> cumulativeSumOfSentences) {
        JSONObject entityJson = new JSONObject();
        entityJson.put("text", mention.text());
        entityJson.put("type", mention.entityType());
        entityJson.put("iFrom", mention.charOffsets().first);
        entityJson.put("iTo", mention.charOffsets().second - 1);
        entityJson.put("wFrom", getTokenWordIdx(mention.tokens().get(0), cumulativeSumOfSentences));
        entityJson.put("wTo", getTokenWordIdx(mention.tokens().get(mention.tokens().size() - 1), cumulativeSumOfSentences));
        return entityJson;
    }

    /**
     * Generate the spaces array out of the original text and the tokens, and output them as #JSONArray object
     * The spaces will be the gaps between each consecutive pair of tokens
     * This method first sort all the tokens by beginning position.
     * Then it proceeds to extract those characters that are between each pair of consecutive tokens and add them as spaces
     *
     * @param originalText the full original text
     * @param tokens       the tokens extracted from that text
     * @return array of spaces that split the tokens
     */
    private JSONArray calculateSpaces(String originalText, List<CoreLabel> tokens) {

        ArrayList<String> spaces = new ArrayList<>();
        //sort tokens by first position
        tokens.sort(Comparator.comparingInt(CoreLabel::beginPosition));
        //first space item
        spaces.add(originalText.substring(0, tokens.get(0).beginPosition()));
        //spaces between tokens
        for (int i = 0; i < tokens.size() - 1; ++i) {
            spaces.add(originalText.substring(tokens.get(i).endPosition(), tokens.get(i + 1).beginPosition()));
        }
        //last space item
        spaces.add(originalText.substring(tokens.get(tokens.size() - 1).endPosition()));
        // create a JSONArray object to add spaces into it
        JSONArray spacesArr = new JSONArray();
        //add them from the list
        for (String space : spaces) {
            spacesArr.put(space);
        }
        return spacesArr;
    }

    /**
     * Split a text into paragraphs
     * The splitting process is done using sentence-split and tokenize components of the CoreNLP pipeline
     *
     * @param text the text to split into paragraphs
     * @return list of paragraphs extracted from the text
     */
    private ArrayList<String> splitIntoParagraph(String text) {
        ArrayList<String> paragraphs = new ArrayList<>();
        ArrayList<String> spaces = new ArrayList<>();
        // creates a StanfordCoreNLP object
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        CoreDocument doc = new CoreDocument(text);
        pipeline.annotate(doc);

        //get the spaces between the sentences
        List<CoreSentence> sentences = doc.sentences();
        //first space item
        spaces.add(text.substring(0, sentences.get(0).charOffsets().first));
        //intermediate spaces
        for (int i = 0; i < sentences.size() - 1; ++i) {
            spaces.add(text.substring(sentences.get(i).charOffsets().second, sentences.get(i + 1).charOffsets().first));
        }
        //last space item
        spaces.add(text.substring(sentences.get(sentences.size() - 1).charOffsets().second));

        /*
            Paragraphs are split using \n character. Therefore once a space between two sentences contain \n character,
            it marks the end of the paragraph.
         */
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sentences.size(); ++i) {
            builder.append(sentences.get(i).text());
            if (spaces.get(i + 1).contains("\n")) { // paragraph break
                paragraphs.add(builder.toString());
                builder = new StringBuilder();
            } else {
                //if not add the space to the paragraph
                builder.append(spaces.get(i + 1));
            }
        }
        // if there is an ongoing paragraph add it also
        if (builder.length() > 0) {
            paragraphs.add(builder.toString());
        }
        return paragraphs;
    }

    /**
     * Pad a number with zeros up to a fixed length and return it as a string
     *
     * @param number  the number to be padded
     * @param padding the total size of the intended string, in which the remaining slots will be filled with zeros
     * @return the padded number as a string
     */
    private String paddingNumber(int number, int padding) {
        StringBuilder res = new StringBuilder();
        int counter = Math.max(0, padding - String.valueOf(number).length());
        while (counter-- > 0) {
            res.append('0');
        }
        res.append(number);
        return res.toString();
    }

    /**
     * Split the text into paragraphs and annotate each paragraph with the annotation pipeline
     * The id of each paragraph will be originalId_XXX where XXX is a three digit number (appended with zero if needed)
     * indicating the paragraph index
     *
     * @param id   the id of the text
     * @param text the text to be annotated
     * @return #ArrayList of #JSONObject each representing the annotated paragraph extracted from the text
     * @see #process(String, String) for the format of the annotations
     */
    public ArrayList<JSONObject> processAsParagraphs(String id, String text) {
        ArrayList<String> paragraphs = splitIntoParagraph(text);
        ArrayList<JSONObject> annotatedArticles = new ArrayList<>();
        int counter = 0;
        for (String paragraph : paragraphs) {
            String subId = id + "_" + paddingNumber(counter, 3);
            annotatedArticles.add(process(subId, paragraph));
            ++counter;
        }
        return annotatedArticles;
    }

    /**
     * Annotate the text with the annotation pipeline
     * The format of the json will be the following:
     * {
     * id: givenId,
     * words: the annotated word list of the text
     * spaces: the spaces between the tokens, s.t. spaces[0]+word[0]+spaces[1]+...+word[n-1]+spaces[n] = text
     * annotations: list of NE extracted from the text
     * }
     * <p>
     * Each annotated word have the following attributes
     * {
     * word: the literal word
     * norm: the norm form (lemma)
     * pos: part of speech
     * ner: named entity recognition that this word belong to
     * iFrom: the index of the <strong>character</strong> that represent the start of the word
     * iTo: the index of the <strong>character</strong> that represent the end of the word
     * }
     * <p>
     * Each annotation has the following attributes
     * {
     * text: named entity as mentioned in the text
     * type: the type of named entity, like: LOCATION, PERSON, ORGANIZATION,..
     * iFrom: the index of the <strong>character</strong> that represent the start of the entity
     * iTo: the index of the <strong>character</strong> that represent the end of the entity
     * wFrom: the index of the <strong>word</strong> that represent the start of the entity
     * wTo: the index of the <strong>word</strong> that represent the end of the entity
     * }
     *
     * @param id   the id of the text
     * @param text the text to be annotated
     * @return the annotated text
     */
    public JSONObject process(String id, String text) {
        //create the annotated article object and add the id of the article
        JSONObject annotatedArticle = new JSONObject();
        annotatedArticle.put("id", id);
        try {
            //create a document object out of the text and annotate it
            CoreDocument doc = new CoreDocument(text);
            pipeline.annotate(doc);
            //get the sentences list
            List<CoreSentence> sentences = doc.sentences();
            //calculate the running sum of the number of words in sentences
            ArrayList<Integer> cumulativeSumOfSetentences = new ArrayList<>();
            int counter = 0;
            for (CoreSentence sentence : sentences) {
                cumulativeSumOfSetentences.add(counter);
                counter += sentence.tokens().size();
            }
            //add the words annotation list
            JSONArray words = new JSONArray();
            List<CoreLabel> tokens = doc.tokens();
            for (CoreLabel token : tokens) {
                words.put(tokenToJson(token));
            }
            annotatedArticle.put("words", words);
            //add the spaces list
            if (tokens.isEmpty()) {
                annotatedArticle.put("spaces", new JSONArray());
            } else {
                annotatedArticle.put("spaces", calculateSpaces(text, tokens));
            }
            //if NER is enabled, add the named entity information in the `annotations` field
            if (NER) {
                JSONArray annotations = new JSONArray();
                for (CoreEntityMention em : doc.entityMentions()) {
                    annotations.put(entityMentionToJson(em, cumulativeSumOfSetentences));
                }
                annotatedArticle.put("annotations", annotations);
            }
            return annotatedArticle;

        } catch (Exception ex) {
            System.out.println(ex);
        }
        // if anything failed, return an empty annotation object (with only id added)
        annotatedArticle = new JSONObject();
        annotatedArticle.put("words", new JSONArray());
        annotatedArticle.put("spaces", new JSONArray());
        annotatedArticle.put("annotations", new JSONArray());
        return annotatedArticle;
    }
}