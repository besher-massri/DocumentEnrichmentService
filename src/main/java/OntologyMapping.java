import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

public class OntologyMapping {
    String inputDir;
    HashMap<String, HashSet<String>> ontologies;

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

    public JSONObject readOntologyFile(String filePath) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            StringBuilder ontologyFile = new StringBuilder();
            String line = "";
            while ((line = reader.readLine()) != null) {
                ontologyFile.append(line).append("\n");
            }
            return new JSONObject(ontologyFile.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loadOntologies() {
        ArrayList<String> fileList = listFilesForFolder(new File(inputDir));
        for (String file : fileList) {
            System.out.println("Processing ontology file: " + file);
            JSONObject ont = readOntologyFile(inputDir + file);
            HashSet<String> ontSet = new HashSet<>();
            for (Object ontItem : ont.getJSONArray("ontologyList")) {
                String term=(String) ontItem;
                ontSet.add(term.replaceAll("-"," "));
            }
            int idx = file.lastIndexOf('.');
            if (idx == -1) {
                idx = file.length();
            }
            ontologies.put(file.substring(0, idx), ontSet);
        }
    }

    public HashSet<String> retrieveOntology(String ontology) {
        if (ontologies.containsKey(ontology)) {
            return ontologies.get(ontology);
        }
        return new HashSet<>();
    }

    private void processTerm(String term, JSONObject ontAnnot, HashSet<String> ontologySet) {
        if (ontologySet.contains(term)) {
            if (!ontAnnot.has(term)) {
                ontAnnot.put(term, 1);
            } else {
                ontAnnot.put(term, ontAnnot.getInt(term) + 1);
            }
        }
    }

    private void processWordAnnotations(JSONArray wordAnnotations, JSONObject matchedOntTerms, HashSet<String> ontologySet) {
        JSONObject wordAnnotNorm = new JSONObject();
        JSONObject wordAnnotSyn = new JSONObject();
        for (int i = 0; i < wordAnnotations.length(); ++i) {
            JSONObject annot = wordAnnotations.getJSONObject(i);
            processTerm(annot.getString("norm"), wordAnnotNorm, ontologySet);
            if (annot.has("synonyms")) {
                JSONArray synonyms = annot.getJSONArray("synonyms");
                for (int j = 0; j < synonyms.length(); ++j) {
                    processTerm(synonyms.getString(j), wordAnnotSyn, ontologySet);
                }
            }
        }
        matchedOntTerms.put("wordAnnot-norm", wordAnnotNorm);
        matchedOntTerms.put("wordAnnot-syn", wordAnnotSyn);
    }

    private void processNEAnnotations(JSONArray NEAnnotations, JSONObject matchedOntTerms, HashSet<String> ontologySet) {
        JSONObject NEName = new JSONObject();
        JSONObject NESyn = new JSONObject();
        for (int i = 0; i < NEAnnotations.length(); ++i) {
            JSONObject annot = NEAnnotations.getJSONObject(i);
            processTerm(annot.getString("text"), NEName, ontologySet);
            if (annot.has("synonyms")) {
                JSONArray synonyms = annot.getJSONArray("synonyms");
                for (int j = 0; j < synonyms.length(); ++j) {
                    processTerm(synonyms.getString(j), NESyn, ontologySet);
                }
            }
        }
        matchedOntTerms.put("NE-name", NEName);
        matchedOntTerms.put("NE-syn", NESyn);
    }

    private void processWikiAnnotations(JSONArray wikiAnnotations, JSONObject matchedOntTerms, HashSet<String> ontologySet) {
        JSONObject wikiName = new JSONObject();
        JSONObject wikiDataClasses = new JSONObject();
        for (int i = 0; i < wikiAnnotations.length(); ++i) {
            JSONObject annot = wikiAnnotations.getJSONObject(i);
            processTerm(annot.getString("name"), wikiName, ontologySet);
            if (annot.has("wikiDataClasses")) {
                JSONArray wdcs = annot.getJSONArray("wikiDataClasses");
                for (int j = 0; j < wdcs.length(); ++j) {
                    processTerm(wdcs.getJSONObject(j).getString("enLabel"), wikiDataClasses, ontologySet);
                }
            }
        }
        matchedOntTerms.put("wiki-name", wikiName);
        matchedOntTerms.put("wiki-WikiDataClasses", wikiDataClasses);
    }

    public OntologyMapping(String inputDir) {
        this.inputDir = inputDir;
        ontologies=new HashMap<>();
        loadOntologies();
    }

    public void MapWithOntology(JSONObject annotatedDocument, String ontology) {
        System.out.println("Mapping with ontology "+ontology);
        JSONObject matchedOntTerms = new JSONObject();
        HashSet<String> ontologySet = retrieveOntology(ontology);
        if (annotatedDocument.has("wiki")) {
            processWikiAnnotations(annotatedDocument.getJSONArray("wiki"), matchedOntTerms, ontologySet);
        }
        if (annotatedDocument.has("NE")) {
            processNEAnnotations(annotatedDocument.getJSONArray("NE"), matchedOntTerms, ontologySet);
        }
        if (annotatedDocument.has("annotatedWords")) {
            processWordAnnotations(annotatedDocument.getJSONObject("annotatedWords").getJSONArray("words"), matchedOntTerms, ontologySet);
        }
        annotatedDocument.put("ontology_terms", matchedOntTerms);
    }
}
