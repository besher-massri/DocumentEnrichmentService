import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/**
 * This program is used to annotate articles using CoreNLP pipeline
 * In full capabilities, text is tokenized into words which are annotated with POS, Lemma(norm),
 * and named entities are extracted from the text
 * <p>
 * The program process files stored in a given directory.
 * Each file should contain list of json files, one per line, each should contain an id column, and a text column.
 * The program will create an output file for each input file, and write the corresponding annotations in it.
 * <p>
 * To use the program, modify the configuration in the config file and then run the program.
 * The configurations are:
 * - @NER: if true, named entity will be extracted, note that this will slow down the process
 * - @temporalEntities: if false, SUTime expression recogniser will be turned off, do this when the language isn't English.
 * - @splitIntoParagraphs: if true, sentence tokenization will be forced to split on the double newlines `\n\n` symbol,
 * which is usually used when splitting between paragraphs. Set this to true if documents are long and you know
 * that double newlines `\n\n` is used in your documents as a paragraph separator.
 * - @idColumnName: The name of the field which represents the id of the document.
 * - @textColumnName: The name of the field which represents the document text.
 * - @inputDir: the input directory where the files are stored
 * - @outputDir: the output directory where the annotation will be stored
 * - @verbose: [0,inf), a number indicating the frequency of outputting the number of articles processed.
 * The number indicates how many articles will be processed before outputting a new announcement. A value of 0 will
 * deactivate it. Note that in all situations, there will be an output sentence for each file.
 * - @writeBatch [0,inf): The number of annotated articles to process before writing on disk. This is used in case that writing
 * on disk is the bottleneck.Lower value means more time, higher value means higher memory usage. A good starting value
 * is 100. A value of 0 will make writing to disk once per file it.
 * - @fileFrom: the index of the file in the directory to start the process from. This is used especially for running
 * multiple instances of the program in parallel
 * - @fileTo: the index of the file in the directory to end the process at (inclusive). This is used especially for
 * running multiple instances of the program in parallel
 * <p>
 * Main class of the program.
 */
public class DocumentsAnnotator {
    private static boolean NER;
    private static boolean temporalEntities;
    private static boolean splitIntoParagraphs;
    private static String idColumnName;
    private static String textColumnName;
    private static int verbose;
    private static int writeBatch;
    private static String inputDir;
    private static String outputDir;
    private static int fileFrom;
    private static int fileTo;
    private static boolean synonyms;
    private static boolean spaces;
    private static boolean indices;
    private static boolean wordAnnotations;
    private static boolean wikiConcepts;
    private static String ontology;
    private static boolean allowAlternativeNames;
    private static boolean hierarchy;
    private static final String configPath = "config/config.json";
    private static DocumentAnnotatorMicroservice annotator;

    /**
     * Loading the configurations of the program from the config file specified by @configPath
     * If loading failed, or there was a problem in assigning values, a set of default values will be assigned instead.
     */
    private static void loadConfig() {
        try {
            String entireFileText = new Scanner(new File(configPath))
                    .useDelimiter("\\A").next();
            JSONObject config = new JSONObject(entireFileText);
            NER = (Boolean) config.get("NER");
            temporalEntities = (Boolean) config.get("temporalEntities");
            splitIntoParagraphs = (Boolean) config.get("splitIntoParagraphs");
            idColumnName = String.valueOf(config.get("idColumnName"));
            textColumnName = String.valueOf(config.get("textColumnName"));
            inputDir = String.valueOf(config.get("inputDir"));
            outputDir = String.valueOf(config.get("outputDir"));
            verbose = (Integer) config.get("verbose");
            writeBatch = (Integer) config.get("writeBatch");
            fileFrom = (Integer) config.get("fileFrom");
            fileTo = (Integer) config.get("fileTo");
            synonyms = (Boolean) config.get("synonyms");
            spaces = (Boolean) config.get("spaces");
            indices = (Boolean) config.get("indices");
            wordAnnotations = (Boolean) config.get("wordAnnotations");
            wikiConcepts = (Boolean) config.get("wikiConcepts");
            ontology = String.valueOf(config.get("ontology"));
            allowAlternativeNames = (Boolean) config.get("allowAlternativeNames");
            hierarchy = (Boolean) config.get("hierarchy");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            NER = true;
            temporalEntities = false;
            splitIntoParagraphs = true;
            idColumnName = "document_id";
            textColumnName = "document_text";
            verbose = 10;
            writeBatch = 100;
            inputDir = "../../Data/legal documents/asjson/";
            outputDir = "data/processed/articles-annotated/";
            fileFrom = 0;
            fileTo = 1000000;
            synonyms = false;
            spaces = true;
            indices = true;
            wordAnnotations = true;
            wikiConcepts = true;
            ontology = "InforMEA";
            allowAlternativeNames = true;
            hierarchy = false;
        }
    }

    /**
     * Get the list of files names from a directory.
     * The directory should be a #File object, which can be created using: new File(dirPath)
     *
     * @param dir The directory of where the files should be extracted
     * @return List of file names that exists in the directory
     */
    private static ArrayList<String> listFilesForFolder(final File dir) {
        ArrayList<String> fileList = new ArrayList<>();
        for (final File fileEntry : Objects.requireNonNull(dir.listFiles())) {
            if (fileEntry.isDirectory()) {
                fileList.addAll(listFilesForFolder(fileEntry));
            } else {
                fileList.add(fileEntry.getName());
            }
        }
        return fileList;
    }

    /**
     * The main method of the program.
     * It first loads the config.
     * Then pass through the files in the directory that falls under the selected range in the config.
     * For each file it reads the articles line by line.
     * For each article it extracts its id and text from the fields specified in the config
     * Then it calls the CoreNLPAPI instance to annotate the given text.
     * If {@link #writeBatch} >0, once the articles processed reach {@link #writeBatch}, the content will be written on
     * disk
     * Proper output statement will be done depending on the value of @verbose
     *
     * @param args The command line args
     */
    public static void main(String[] args) {
        //loading the config
        loadConfig();
        //initiating the pipeline and output list
/*
        CoreNLPAPI corenlp = new CoreNLPAPI(NER, splitIntoParagraphs, temporalEntities);
        corenlp.setSynonyms(synonyms);
        corenlp.setIndices(indices);
        corenlp.setSpaces(spaces);
        corenlp.setWordAnnotations(wordAnnotations);
*/
        annotator = new DocumentAnnotatorMicroservice();
        List<DocumentEnricher> enrichers = annotator.preparePipeLine(NER, wordAnnotations, synonyms, splitIntoParagraphs, indices, spaces, wikiConcepts, allowAlternativeNames, hierarchy);

        ArrayList<JSONObject> output = new ArrayList<>();

        //getting the names of the files in the directory
        ArrayList<String> fileList = listFilesForFolder(new File(inputDir));

        int itemCounter = 0;
        int errorCounter = 0;
        int totalFilesProcessed = 0;
        //pass through the files in the selected range
        for (int fileCounter = fileFrom; fileCounter < fileList.size() && fileCounter <= fileTo; fileCounter++) {
            String file = fileList.get(fileCounter);
            System.out.println("Processing file: " + file);
            try {

                BufferedReader reader = new BufferedReader(new FileReader(inputDir + file));
                String articleJson;
                PrintWriter out = new PrintWriter(outputDir + file);
                //read one article per line
                while ((articleJson = reader.readLine()) != null) {
                    String articleId;
                    String articleText;
                    ++itemCounter;
                    try {
                        //extract the id and text
                        JSONObject article = new JSONObject(articleJson);
                        articleId = String.valueOf(article.get(idColumnName));
                        articleText = (String) article.get(textColumnName);
                    } catch (Exception e) {
                        articleId = String.valueOf(itemCounter);
                        articleText = "";
                        errorCounter++;
                        System.out.println("Found " + errorCounter + " errors");
                    }
                    //annotate the article

                    JSONObject annotation = annotator.annotateDocument(articleId, articleText, enrichers, ontology);
                    assert annotation != null;
                    output.add(annotation);
                    if (writeBatch > 0 && itemCounter % writeBatch == 0) {
                        for (JSONObject object : output) {
                            object.write(out);
                            out.println();
                        }
                        output.clear();
                    }
                    //state progress when number of articles processed is a multiple of verbose (if verbose is activated)
                    if (verbose > 0 && itemCounter % verbose == 0) {
                        System.out.println("Processed " + itemCounter + " articles");
                    }
                }
                //write any remaining annotations to the disk (will be all annotations if writeBatch is big or disabled
                for (JSONObject object : output) {
                    object.write(out);
                    out.println();
                }
                output.clear();
                out.close();
                ++totalFilesProcessed;
                System.out.println("Processed " + totalFilesProcessed + " files");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Processing Finished");
        System.out.println("Total Files Processed: " + totalFilesProcessed);
        System.out.println("Total Articles Processed: " + itemCounter);
    }
}