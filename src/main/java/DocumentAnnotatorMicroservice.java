import org.json.JSONObject;

public class DocumentAnnotatorMicroservice {
    private CoreNLPAPI NER_SplitIntoParagraphsPipeline;
    private CoreNLPAPI NERPipeline;
    private CoreNLPAPI SplitIntoParagraphsPipeline;
    private CoreNLPAPI NonePipeline;

    private void initPipelines() {
        NER_SplitIntoParagraphsPipeline = new CoreNLPAPI(true, true, true);
        SplitIntoParagraphsPipeline = new CoreNLPAPI(false, true, true);
        NERPipeline = new CoreNLPAPI(true, false, true);
        NonePipeline = new CoreNLPAPI(false, false, true);
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
                                                 boolean indices, boolean spaces) {
        System.out.println("Entered initialization");
        CoreNLPAPI corenlp = getSuitablePipeline(NER, splitIntoParagraphs);
        System.out.println("Finished initialization");
        corenlp.setSpaces(spaces);
        corenlp.setIndices(indices);
        corenlp.setWordAnnotations(wordAnnotations);
        corenlp.setSynonyms(synonyms);
        System.out.println("Finished initialization");
        return corenlp;
    }

    public DocumentAnnotatorMicroservice() {
        initPipelines();
    }

    public JSONObject annotateDocument(String id, String text,
                                       Boolean NER, Boolean wordAnnotations, Boolean synonyms,
                                       Boolean splitIntoParagraphs, Boolean indices, Boolean spaces) {
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
        System.out.println("Configurations Set");
        CoreNLPAPI corenlp = initializePipelineConfigs(NER, wordAnnotations, splitIntoParagraphs, synonyms, indices, spaces);
        System.out.println("Pipeline Initialized");
        return corenlp.process(id, text);
    }
}
