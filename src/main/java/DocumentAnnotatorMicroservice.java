import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class DocumentAnnotatorMicroservice {
    private CoreNLPAPI NER_SplitIntoParagraphsPipeline;
    private CoreNLPAPI NERPipeline;
    private CoreNLPAPI SplitIntoParagraphsPipeline;
    private CoreNLPAPI NonePipeline;
    private Wikification wikification;

    private OntologyMapping ontMapping;
    private volatile List<JSONObject> enrichments;

    private String wikifierWebsite, wikifierKey;
    private int wikifierThreads, wikifierMaxLength;
    private String ontologyDir;

    private void loadEnv() {

        Dotenv dotenv = Dotenv.configure()
                .directory("config/")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        ontologyDir = dotenv.get("ONTOLOGY_DIR");
        wikifierKey = dotenv.get("WIKIFIER_USERKEY");
        wikifierWebsite = dotenv.get("WIKIFIER_URL");
        wikifierMaxLength = Integer.parseInt(Objects.requireNonNull(dotenv.get("WIKIFIER_MAX_LENGTH")));
        wikifierThreads = Integer.parseInt(Objects.requireNonNull(dotenv.get("WIKIFIER_N_THREADS")));

    }

    private void initPipelines() {

        NER_SplitIntoParagraphsPipeline = new CoreNLPAPI(true, true, true);
        SplitIntoParagraphsPipeline = new CoreNLPAPI(false, true, true);
        NERPipeline = new CoreNLPAPI(true, false, true);
        NonePipeline = new CoreNLPAPI(false, false, true);
        wikification = new Wikification(wikifierKey, wikifierWebsite, wikifierMaxLength, wikifierThreads);
        try {
            ontMapping = new OntologyMapping(ontologyDir, true, false);
        } catch (IOException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        System.out.println("Pipeline Initialized");
    }

    private CoreNLPAPI getSuitablePipeline(boolean NER, boolean splitIntoParagraphs) {
        if (NER) {
            if (splitIntoParagraphs) {
                return NER_SplitIntoParagraphsPipeline;
            }
            return NERPipeline;
        }
        // not NER
        if (splitIntoParagraphs) {
            return SplitIntoParagraphsPipeline;
        }
        return NonePipeline;
    }

    private CoreNLPAPI initializePipelineConfigs(boolean NER, boolean wordAnnotations, boolean splitIntoParagraphs, boolean synonyms,
                                                 boolean indices, boolean spaces, boolean allowAlternativeNames, boolean hierarchy) {
        System.out.println("Entered initialization");
        CoreNLPAPI corenlp = getSuitablePipeline(NER, splitIntoParagraphs);
        System.out.println("Finished initialization");
        corenlp.setSpaces(spaces);
        corenlp.setIndices(indices);
        corenlp.setWordAnnotations(wordAnnotations);
        corenlp.setSynonyms(synonyms);
        ontMapping.setAllowAlternativeNames(allowAlternativeNames);
        ontMapping.setHierarchy(hierarchy);
        System.out.println("Finished initialization");
        return corenlp;
    }

    private String cleanText(String articleText) {
        StringBuilder cleanText = new StringBuilder();
        String snippet;
        for (int i = 0; i * 1000 < articleText.length(); ++i) {
            snippet = articleText.substring(1000 * i, Math.min(1000 * i + 1000, articleText.length()));
            boolean isValid = false;
            for (int j = 0; j < snippet.length() && !isValid; ++j) {
                isValid |= Character.isAlphabetic(snippet.charAt(j));
            }
            if (isValid) {
                cleanText.append(snippet);
            }
        }
        return cleanText.toString();
    }

    public DocumentAnnotatorMicroservice() {
        loadEnv();
        initPipelines();
    }

    private long execute(DocumentEnricher task, String id, String text) {
        long startTime = System.currentTimeMillis();
        enrichments.add(task.process(id, text));
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }


    public List<DocumentEnricher> preparePipeLine(Boolean NER, Boolean wordAnnotations, Boolean synonyms,
                                                  Boolean splitIntoParagraphs, Boolean indices, Boolean spaces,
                                                  Boolean wikiConcepts, boolean allowAlternativeNames, boolean hierarchy) {
        List<DocumentEnricher> tasks = new ArrayList<>();
        enrichments = new ArrayList<>();
        System.out.println("Configurations Set");
        if (wordAnnotations || NER) {
            tasks.add(initializePipelineConfigs(NER, wordAnnotations, splitIntoParagraphs, synonyms, indices, spaces, allowAlternativeNames, hierarchy));
        }
        if (wikiConcepts) {
            tasks.add(wikification);
        }
        System.out.println("Pipeline Initialized");
        return tasks;
    }

    public JSONObject annotateDocument(String id, String text, List<DocumentEnricher> tasks, String ontology) {
        text = cleanText(text);
        boolean paralleizeTasks = true;
        if (paralleizeTasks) {
            try {
                String finalText = text;
                ExecutorService.parallelize(tasks, (task) -> {
                    return execute(task, id, finalText);
                }, 2);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        } else {
            for (DocumentEnricher task : tasks) {
                execute(task, id, text);
            }
        }
        JSONObject annotatedDocument = new JSONObject();
        annotatedDocument.put("id", id);
        annotatedDocument.put("text", text);
        JSONObject annotationsObj = new JSONObject();
        for (JSONObject enrichment : enrichments) {
            for (String key : enrichment.keySet()) {
                annotationsObj.put(key, enrichment.get(key));
            }
        }
        annotatedDocument.put("annotations", annotationsObj);
        if (!ontology.equals("")) {
            ontMapping.MapWithOntology(annotationsObj, ontology);
        }
        return annotatedDocument;
    }

    public JSONObject annotateDocument(String id, String text,
                                       Boolean NER, Boolean wordAnnotations, Boolean synonyms,
                                       Boolean splitIntoParagraphs, Boolean indices, Boolean spaces,
                                       Boolean wikiConcepts,
                                       String ontology, Boolean allowAlternativeNames, Boolean hierarchy) {
        System.out.println("Annotation started");
        //initiate default parameters if null
        if (NER == null) {
            NER = true;
        }
        if (wordAnnotations == null) {
            wordAnnotations = true;
        }
        if (synonyms == null) {
            synonyms = true;
        }
        if (splitIntoParagraphs == null) {
            splitIntoParagraphs = true;
        }
        if (indices == null) {
            indices = false;
        }
        if (spaces == null) {
            spaces = false;
        }
        if (wikiConcepts == null) {
            wikiConcepts = true;
        }
        if (ontology == null) {
            ontology = "InforMEA";
        }
        if (allowAlternativeNames == null) {
            allowAlternativeNames = true;
        }
        if (hierarchy == null) {
            hierarchy = false;
        }
        List<DocumentEnricher> tasks = preparePipeLine(NER, wordAnnotations, synonyms, splitIntoParagraphs, indices, spaces, wikiConcepts, allowAlternativeNames, hierarchy);
        return annotateDocument(id, text, tasks, ontology);

    }
}
