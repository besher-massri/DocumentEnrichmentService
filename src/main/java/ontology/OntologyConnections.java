package ontology;

import java.util.*;

public class OntologyConnections {
    private HashMap<String, List<String>> inDegree;//parents
    private HashMap<String, List<String>> outDegree;//children

    OntologyConnections(){
        inDegree=new HashMap<>();
        outDegree=new HashMap<>();
    }
    private void addToCollection(String id, String item, HashMap<String, List<String>> collection) {
        if (collection.containsKey(id)) {
            collection.get(id).add(item);
        } else {
            List<String> arr = new ArrayList<>();
            arr.add(item);
            collection.put(id, arr);
        }
    }

    public void addConnection(String from, String to) {
        addToCollection(to, from, inDegree);
        addToCollection(from, to, outDegree);
    }

    private void getParents(String term, Set<String> parents) {
        parents.add(term);
        if (inDegree.containsKey(term)) {
            for (String parentTerm : inDegree.get(term)) {
                if (!parents.contains(parentTerm)){
                    getParents(parentTerm, parents);
                }
            }
        }
    }

    public Set<String> getParents(String term) {
        HashSet<String> parents = new HashSet<>();
        getParents(term, parents);
        return parents;
    }

}
