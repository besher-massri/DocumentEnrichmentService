import ontology.Ontology;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OntologyMapping {
    private String inputDir;
    private boolean allowAlternativeNames;
    private boolean hierarchy;
    private HashMap<String, Ontology> ontologies;

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

    public void setAllowAlternativeNames(boolean allowAlternativeNames) {
        this.allowAlternativeNames = allowAlternativeNames;
    }

    public void setHierarchy(boolean hierarchy) {
        this.hierarchy = hierarchy;
    }

    private void loadOntologies() throws IOException, NoSuchFieldException {
        ArrayList<String> fileList = listFilesForFolder(new File(inputDir));
        for (String file : fileList) {
            System.out.println("Processing ontology file: " + file);
            HashSet<String> ontSet = new HashSet<>();
            int idx = file.lastIndexOf('.');
            if (idx == -1) {
                idx = file.length();
            }
            String ontologyName = file.substring(0, idx);
            Ontology ont = new Ontology(ontologyName, inputDir + file);
            ontologies.put(ontologyName, ont);
        }
    }

    private void processTerm(String term, HashMap<String, JSONArray> ontAnnot, Ontology ontology, String language) {
        JSONArray ontTerms = ontology.processTerm(term, language, allowAlternativeNames, hierarchy);
        if (ontTerms == null || ontTerms.length() == 0) {
            return;
        }
        //if not, then there is at least one occurrence
        //first, check if the term is in the hashmap at all
        if (!ontAnnot.containsKey(term)) {
            ontAnnot.put(term, new JSONArray());
        }
        for (Object ontTerm_ : ontTerms) {
            JSONObject ontTerm = (JSONObject) ontTerm_;
            String ontTermId = ontTerm.getString("id");
            //go over the terms in the matched ontology terms, check if term's original ontology term exist (the same term can exists in multiple ontology terms as alternative_names)
            boolean exists = false;
            JSONArray matchedOntTerms = ontAnnot.get(term);
            for (Object matchedOntTerm_ : matchedOntTerms) {
                JSONObject matchedOntTerm = (JSONObject) matchedOntTerm_;
                if (matchedOntTerm.get("id").equals(ontTermId)) {
                    exists = true;
                    matchedOntTerm.put("freq", matchedOntTerm.getInt("freq") + 1);
                }
            }
            //if not exists add a new one, with a frequency of 1
            if (!exists) {
                ontTerm.put("freq", 1);
                matchedOntTerms.put(ontTerm);
            }
        }
    }

    private void processWordAnnotations(JSONArray wordAnnotations, JSONObject matchedOntTerms, Ontology ontology, String language) {
        HashMap<String, JSONArray> wordAnnotNorm = new HashMap<>();
        HashMap<String, JSONArray> wordAnnotSyn = new HashMap<>();
        for (int i = 0; i < wordAnnotations.length(); ++i) {
            JSONObject annot = wordAnnotations.getJSONObject(i);
            processTerm(annot.getString("norm"), wordAnnotNorm, ontology, language);
            if (annot.has("synonyms")) {
                JSONArray synonyms = annot.getJSONArray("synonyms");
                for (int j = 0; j < synonyms.length(); ++j) {
                    processTerm(synonyms.getString(j), wordAnnotSyn, ontology, language);
                }
            }
        }
        matchedOntTerms.put("wordAnnot-norm", wordAnnotNorm);
        matchedOntTerms.put("wordAnnot-syn", wordAnnotSyn);
    }

    private void processNEAnnotations(JSONArray NEAnnotations, JSONObject matchedOntTerms, Ontology ontology, String language) {
        HashMap<String, JSONArray> NEName = new HashMap<>();
        HashMap<String, JSONArray> NESyn = new HashMap<>();
        for (int i = 0; i < NEAnnotations.length(); ++i) {
            JSONObject annot = NEAnnotations.getJSONObject(i);
            processTerm(annot.getString("text"), NEName, ontology, language);
            if (annot.has("synonyms")) {
                JSONArray synonyms = annot.getJSONArray("synonyms");
                for (int j = 0; j < synonyms.length(); ++j) {
                    processTerm(synonyms.getString(j), NESyn, ontology, language);
                }
            }
        }
        matchedOntTerms.put("NE-name", NEName);
        matchedOntTerms.put("NE-syn", NESyn);
    }

    private void processWikiAnnotations(JSONArray wikiAnnotations, JSONObject matchedOntTerms, Ontology ontology, String language) {
        HashMap<String, JSONArray> wikiName = new HashMap<>();
        HashMap<String, JSONArray> wikiDataClasses = new HashMap<>();
        for (int i = 0; i < wikiAnnotations.length(); ++i) {
            JSONObject annot = wikiAnnotations.getJSONObject(i);
            processTerm(annot.getString("name"), wikiName, ontology, language);
            if (annot.has("wikiDataClasses")) {
                JSONArray wdcs = annot.getJSONArray("wikiDataClasses");
                for (int j = 0; j < wdcs.length(); ++j) {
                    processTerm(wdcs.getJSONObject(j).getString("enLabel"), wikiDataClasses, ontology, language);
                }
            }
        }
        matchedOntTerms.put("wiki-name", wikiName);
        matchedOntTerms.put("wiki-WikiDataClasses", wikiDataClasses);
    }

    public OntologyMapping(String inputDir, boolean allowAlternativeNames, boolean hierarchy) throws IOException, NoSuchFieldException {
        this.inputDir = inputDir;
        this.allowAlternativeNames = allowAlternativeNames;
        this.hierarchy = hierarchy;
        ontologies = new HashMap<>();
        loadOntologies();
    }

    public void MapWithOntology(JSONObject annotatedDocument, String ontologyName) {
        List<Ontology> chosenOntologies = new ArrayList<>();
        String language;
        if (annotatedDocument.has("lang")) {
            language = annotatedDocument.getString("lang");
        } else {
            language = "eng";
        }
        if (ontologyName.equals("ALL")) {
            chosenOntologies.addAll(ontologies.values());
        } else if (ontologies.containsKey(ontologyName)) {
            chosenOntologies.add(ontologies.get(ontologyName));
        } else {
            return;
        }
        annotatedDocument.put("ontology_terms", new JSONObject());
        JSONObject ontologyTerms = annotatedDocument.getJSONObject("ontology_terms");
        for (Ontology ontology : chosenOntologies) {
            JSONObject matchedOntTerms = new JSONObject();
            System.out.println("Mapping with ontology " + ontology.getOntologyName());
            if (annotatedDocument.has("wiki")) {
                processWikiAnnotations(annotatedDocument.getJSONArray("wiki"), matchedOntTerms, ontology, language);
            }
            if (annotatedDocument.has("NE")) {
                processNEAnnotations(annotatedDocument.getJSONArray("NE"), matchedOntTerms, ontology, language);
            }
            if (annotatedDocument.has("annotatedWords")) {
                processWordAnnotations(annotatedDocument.getJSONObject("annotatedWords").getJSONArray("words"), matchedOntTerms, ontology, language);
            }
            ontologyTerms.put(ontology.getOntologyName(), matchedOntTerms);
        }
    }
}
