package ontology;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Ontology {

    private String ontologyName;
    private HashMap<String, OntologyNode> nodes;
    private HashMap<String, List<OntologyNode>> termsToNode;
    private HashMap<String, List<OntologyNode>> termsWithAlternativesToNode;
    private OntologyConnections links;

    private void init(String name) {
        this.ontologyName = name;
        nodes = new HashMap<>();
        termsToNode = new HashMap<>();
        termsWithAlternativesToNode = new HashMap<>();
        links = new OntologyConnections();
    }

    public String readAllFile(String filePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        StringBuilder builder = new StringBuilder();
        String line = "";
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    void addTermToMapping(String term, OntologyNode node, HashMap<String, List<OntologyNode>> mapping) {
        if (mapping.containsKey(term)) {
            mapping.get(term).add(node);
        } else {
            List<OntologyNode> arr = new ArrayList<>();
            arr.add(node);
            mapping.put(term, arr);
        }
    }

    private void parseLinks(JSONArray linksArr) {
        for (Object link : linksArr) {
            JSONArray linkArr = (JSONArray) link;
            links.addConnection(linkArr.getString(0), linkArr.getString(1));
        }
    }

    private void parseTerms(JSONArray termsArr) throws NoSuchFieldException {
        for (Object term : termsArr) {
            OntologyNode node = new OntologyNode((JSONObject) term);
            nodes.put(node.getId(), node);
            addTermToMapping(node.getName(), node, termsToNode);
            List<String> alternative_names = node.getAlternativeNames();
            addTermToMapping(node.getName(), node, termsWithAlternativesToNode);
            for (String alternative_name : alternative_names) {
                addTermToMapping(alternative_name, node, termsWithAlternativesToNode);
            }
        }
    }

    public Ontology(String name, String filePath) throws IOException, NoSuchFieldException {
        init(name);
        String str = readAllFile(filePath);
        JSONObject ontology = new JSONObject(str);
        if (!ontology.has("terms")) {
            throw new NoSuchFieldException("terms");
        }
        if (!ontology.has("links")) {
            throw new NoSuchFieldException("links");
        }
        parseLinks(ontology.getJSONArray("links"));
        parseTerms(ontology.getJSONArray("terms"));
    }

    public boolean termExists(String term, boolean allowAlternativeNames) {
        if (allowAlternativeNames) {
            return termsWithAlternativesToNode.containsKey(term);
        }
        return termsToNode.containsKey(term);
    }

    private JSONArray processOntologyNode(String term, List<OntologyNode> nodeArr, String language, boolean hierarchy) {
        JSONArray arr = new JSONArray();
        for (OntologyNode node : nodeArr) {
            JSONObject obj = new JSONObject();
            obj.put("id", node.getId());
            obj.put("term", node.getName());
            obj.put("matched_term", term);
            obj.put("alternative_names", node.getAlternativeNames());
            obj.put("source", ontologyName);
            obj.put("language", language);
            if (hierarchy) {
                obj.put("hierarchy", new JSONArray(links.getParents(node.getId())));
            }
            arr.put(obj);
        }
        return arr;
    }

    public JSONArray processTerm(String term, String language, boolean allowAlternativeNames, boolean hierarchy) {
        if (!termExists(term, allowAlternativeNames)) {
            return null;
        }
        if (allowAlternativeNames) {
            return processOntologyNode(term, termsWithAlternativesToNode.get(term), language, hierarchy);
        }
        return processOntologyNode(term, termsToNode.get(term), language, hierarchy);
    }

    public String getOntologyName() {
        return ontologyName;
    }
}
