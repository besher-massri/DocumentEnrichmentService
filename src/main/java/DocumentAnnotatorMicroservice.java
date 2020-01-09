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
        //System.out.println("Pipeline Initialized");
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
        //System.out.println("Entered initialization");
        CoreNLPAPI corenlp = getSuitablePipeline(NER, splitIntoParagraphs);
        //System.out.println("Finished initialization");
        corenlp.setSpaces(spaces);
        corenlp.setIndices(indices);
        corenlp.setWordAnnotations(wordAnnotations);
        corenlp.setSynonyms(synonyms);
        ontMapping.setAllowAlternativeNames(allowAlternativeNames);
        ontMapping.setHierarchy(hierarchy);
        //System.out.println("Finished initialization");
        return corenlp;
    }

    private String cleanText(String articleText) {
        StringBuilder cleanText = new StringBuilder();
        int offset = 0;
        while (offset < articleText.length() && articleText.substring(offset, offset + 2).equals("\\n")) {
            offset += 2;
        }
        int trim = articleText.length() - 2;
        while (trim > offset && articleText.substring(trim, trim + 2).equals("\\n")) {
            trim -= 2;
        }
        trim += 2;
        if (trim - offset <= 0) {
            return "";
        }
        int cnt = 0;
        /*
        for (int i = offset; i < trim; i += 1000) {
            boolean isValid = false;
            int st = i;
            int en = Math.min(st + 1000, trim);
            for (int j = st; j < en && !isValid; ++j) {
                isValid = !Character.isWhitespace(articleText.charAt(j));
            }
            //snippet = articleText.substring(st, en);
            if (isValid) {
                cleanText.append(articleText, st, en);
            }
        }*/
        for (int i = offset; i < trim - 1; ++i) {
            char c = articleText.charAt(i);
            char nxt = articleText.charAt(i + 1);
            if (c=='\\' && nxt=='n'){
                ++cnt;
                if (cnt<3){
                    cleanText.append("\\n");
                }
                ++i;
            }else{
                cnt=0;
                cleanText.append(c);
            }
        }
        return cleanText.toString();
    }

    public DocumentAnnotatorMicroservice() {
        loadEnv();
        initPipelines();
    }

    private long execute(DocumentEnricher task, String id, List<String> texts, List<String> languages) {
        long startTime = System.currentTimeMillis();
        enrichments.add(task.process(id, texts, languages));
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    public List<DocumentEnricher> preparePipeLine(List<String> languages, Boolean NER, Boolean wordAnnotations, Boolean synonyms,
                                                  Boolean splitIntoParagraphs, Boolean indices, Boolean spaces,
                                                  Boolean wikiConcepts, boolean allowAlternativeNames, boolean hierarchy) {
        List<DocumentEnricher> tasks = new ArrayList<>();
        enrichments = new ArrayList<>();
        //System.out.println("Configurations Set");
        if ((languages.contains("en") || languages.contains("xx")) && (wordAnnotations || NER)) {
            tasks.add(initializePipelineConfigs(NER, wordAnnotations, splitIntoParagraphs, synonyms, indices, spaces, allowAlternativeNames, hierarchy));
        }
        if (wikiConcepts) {
            tasks.add(wikification);
        }
        //System.out.println("Pipeline Initialized");
        return tasks;
    }

    public JSONObject annotateDocument(String id, List<String> texts, List<String> languages, List<DocumentEnricher> tasks, String ontology) {
        assert (texts.size() == languages.size());
        JSONObject annotatedDocument = new JSONObject();
        annotatedDocument.put("id", id);
        boolean parallelizeTasks = true;
        for (int i = 0; i < texts.size(); ++i) {
            texts.set(i, cleanText(texts.get(i)));
        }
        if (parallelizeTasks) {
            try {
                ExecutorService.parallelize(tasks, (task) -> {
                    return execute(task, id, texts, languages);
                }, 2);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        } else {
            for (DocumentEnricher task : tasks) {
                execute(task, id, texts, languages);
            }
        }
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

    public JSONObject annotateDocument(String id, List<String> texts, List<String> languages,
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
        List<DocumentEnricher> tasks = preparePipeLine(languages, NER, wordAnnotations, synonyms, splitIntoParagraphs, indices, spaces, wikiConcepts, allowAlternativeNames, hierarchy);
        return annotateDocument(id, texts, languages, tasks, ontology);

    }
}
