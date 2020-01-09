import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Wikification implements DocumentEnricher {
    private String userKey;
    private String wikifierUrl;
    private int maxLength;
    private int nThreads;
    private volatile List<JSONObject> conceptsList = new ArrayList<>();

    public Wikification(String userKey, String wikifierUrl, int maxlength, int nThreads) {
        this.userKey = userKey;
        this.wikifierUrl = wikifierUrl;
        this.maxLength = maxlength;
        this.nThreads = nThreads;
    }

    public Wikification(String userKey, String wikifierUrl) {
        this(userKey, wikifierUrl, 10000, 5);
    }

    private JSONObject wikifyText(String text) {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost request = new HttpPost(wikifierUrl + "annotate-article");
            List<NameValuePair> body = new ArrayList<>();
            body.add(new BasicNameValuePair("text", text));
            body.add(new BasicNameValuePair("lang", "auto"));
            body.add(new BasicNameValuePair("support", "true"));
            body.add(new BasicNameValuePair("ranges", "false"));
            body.add(new BasicNameValuePair("includeCosines", "true"));
            body.add(new BasicNameValuePair("userKey", userKey));
            body.add(new BasicNameValuePair("nTopDfValuesToIgnore", "50"));
            body.add(new BasicNameValuePair("nWordsToIgnoreFromList", "50"));
            request.addHeader("content-type", "application/x-www-form-urlencoded");
            request.setEntity(new UrlEncodedFormEntity(body));
            HttpResponse result = httpClient.execute(request);

            String json = EntityUtils.toString(result.getEntity(), "UTF-8");
            try {
                return new JSONObject(json);
            } catch (Exception e) {
                return new JSONObject("{}");
            }

        } catch (IOException ex) {
            return new JSONObject("{}");
        }
    }

    class PairRankConcept implements Comparable<PairRankConcept> {
        double rank;
        JSONObject concept;

        public PairRankConcept(Object obj) {
            concept = (JSONObject) obj;
            rank = concept.getDouble("pageRank");
        }

        @Override
        public int compareTo(PairRankConcept pairRankConcept) {
            if (Math.abs(rank - pairRankConcept.rank) < 1e-6) {
                return 0;
            }
            if (rank > pairRankConcept.rank) {
                return -1;
            }
            return 1;
        }
    }

    class Task {
        String text;
        double weight;
        long totalTimeConsumed;

        public Task(String chunk, double weight) {
            this.text = chunk;
            this.weight = weight;
        }

        public List<JSONObject> executeTask() {
            long startTime = System.currentTimeMillis();
            JSONObject json = wikifyText(text);
            if (!json.has("annotations") || json.getJSONArray("annotations").length() == 0) {
                return new ArrayList<>();
            }
            JSONArray annotations = json.getJSONArray("annotations");
            Iterator<Object> it = annotations.iterator();
            List<PairRankConcept> objList = new ArrayList<>();
            while (it.hasNext()) {
                objList.add(new PairRankConcept(it.next()));
            }
            Collections.sort(objList);
            /*
             * get top wikipedia concepts
             */
            // calculate total pageRank from all concepts
            double totalRank = 0;
            for (PairRankConcept con : objList) {
                totalRank += con.rank * con.rank;
            }

            // get top 80% concepts - noise reduction
            double partial = 0;
            for (int i = 0; i < objList.size(); ++i) {
                double rank = objList.get(i).rank;
                partial += rank * rank;
                if (partial / totalRank > 0.8) {
                    objList = objList.subList(0, i + 1);
                    break;
                }
            }
            /*
             * prepare concepts
             */
            List<JSONObject> concepts = new ArrayList<>();
            // create concept list
            for (PairRankConcept concept : objList) {
                JSONObject conceptInfo = new JSONObject();
                JSONObject oldConcept = concept.concept;
                conceptInfo.put("uri", oldConcept.getString("url"));
                JSONObject lang_info = new JSONObject();
                lang_info.put("name", oldConcept.getString("title"));
                lang_info.put("uri", oldConcept.getString("url"));
                // the frequency of this language of this concept
                lang_info.put("freq", 1);
                JSONObject lang = new JSONObject();
                try {
                    lang.put(oldConcept.getString("lang"), lang_info);
                    conceptInfo.put("langInfo", lang);
                    conceptInfo.put("uri", oldConcept.has("secUrl") ? oldConcept.getString("secUrl") : oldConcept.getString("url"));
                    conceptInfo.put("secUri", oldConcept.has("secUrl") ? oldConcept.getString("secUrl") : null);
                    conceptInfo.put("secName", oldConcept.has("secTitle") ? oldConcept.getString("secTitle") : null);
                    conceptInfo.put("lang", oldConcept.getString("lang"));
                    conceptInfo.put("wikiDataClasses", oldConcept.has("wikiDataClasses") ? oldConcept.getJSONArray("wikiDataClasses") : null);
                    conceptInfo.put("cosine", oldConcept.getDouble("cosine") * weight);
                    conceptInfo.put("pageRank", oldConcept.getDouble("pageRank") * weight);
                    conceptInfo.put("dbPediaIri", oldConcept.getString("dbPediaIri"));
                    conceptInfo.put("supportLen", oldConcept.getInt("supportLen"));
                } catch (Exception e) {
                    System.out.println("Error at " + conceptInfo.getString("url"));
                }

                concepts.add(conceptInfo);
            }
            long stopTime = System.currentTimeMillis();
            totalTimeConsumed = stopTime - startTime;
            return concepts;
        }
    }

    private List<Task> prepareWikificationTasks(String text) {
// set placeholders
        List<Task> tasks = new ArrayList<>();
        int textIndex = 0;

        // go through whole text
        while (text.length() > textIndex) {
            // get the text chunk
            String chunk = text.substring(textIndex, Math.min(textIndex + maxLength, text.length()));
            // there is not text to be processed, break the cycle
            if (chunk.length() == 0) {
                break;
            }
            if (chunk.length() == maxLength) {
                // text chunk is of max length - make a cutoff at last
                // end character to avoid cutting in the middle of sentence
                int cutoff = 0;
                List<String> lastCharacter = new ArrayList<>();
                Matcher m = Pattern.compile("[\\.?!]")
                        .matcher(chunk);
                while (m.find()) {
                    lastCharacter.add(m.group());
                }
                if (lastCharacter.size() > 0) {
                    cutoff = chunk.lastIndexOf(lastCharacter.get(lastCharacter.size() - 1));
                }
                // if there is not end character detected
                if (cutoff == 0) {
                    cutoff = chunk.lastIndexOf(' ');
                }
                //if there is not space detected
                if (cutoff == 0 || cutoff == -1) {
                    cutoff = chunk.lastIndexOf('\n');
                }
                // if there is not newline detected - cut of the whole chunk
                if (cutoff == 0 || cutoff == -1) {
                    cutoff = chunk.length();
                }
                // get the chunk
                chunk = chunk.substring(0, cutoff);
                // increment text index
                textIndex += cutoff;
            } else {
                // we got to the end of text
                textIndex += maxLength;
            }
            // calculate the weight we add to the found wikipedia concepts
            double weight = chunk.length() * 1.0 / text.length();
            // add a new wikification task on text chunk
            tasks.add(new Task(chunk, weight));
        }
        return tasks;
    }

    Long execute(Task task) {
        conceptsList.addAll(task.executeTask());
        return task.totalTimeConsumed;
    }

    public JSONObject process(String id, List<String> texts, List<String> languages) {
        conceptsList.clear();
        //combine all texts from all languages into tasks
        List<Task> tasks = new ArrayList<>();
        for (String text : texts) {
            tasks.addAll(prepareWikificationTasks(text));
        }
        List<JSONObject> concepts = new ArrayList<>();
        //should be paralleled
        int taskCounter = 0;
        try {
            ExecutorService.parallelize(tasks, (task) -> {
                return execute(task);
            }, nThreads);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
      /*
        for (Task task : tasks) {
            System.out.println("Executing task "+taskCounter);
            taskCounter++;
            execute(task);
        }
*/
        if (conceptsList.isEmpty()) {
            return new JSONObject();
        }

        HashMap<String, JSONObject> conceptsMap = new HashMap<>();
        // merge concepts with matching uri
        for (JSONObject concept : conceptsList) {
            String uri = concept.getString("uri");
            //there is only one initially, which is the original language of the concept
            JSONObject conceptLangInfo = concept.getJSONObject("langInfo");
            String conceptLang = conceptLangInfo.keys().next();
            JSONObject langConceptLangInfo = conceptLangInfo.getJSONObject(conceptLang);//the specific language concept info
            if (conceptsMap.containsKey(uri)) {
                // concept exists in mapping - add weighted pageRank
                JSONObject mergeConcept = conceptsMap.get(uri);
                JSONObject langInfo = mergeConcept.getJSONObject("langInfo");
                //if there is no occurrence of this language info (yet), add it
                if (!langInfo.has(conceptLang)) {
                    langInfo.put(conceptLang, langConceptLangInfo);
                } else {//else increase its frequency
                    langConceptLangInfo.put("freq", langConceptLangInfo.getInt("freq") + 1);
                }
                mergeConcept.put("pageRank", mergeConcept.getDouble("pageRank") + concept.getDouble("pageRank"));
                mergeConcept.put("cosine", mergeConcept.getDouble("cosine") + concept.getDouble("cosine"));
                mergeConcept.put("supportLen", mergeConcept.getInt("supportLen") + concept.getInt("supportLen"));
            } else {
                //  add concept to the mapping
                conceptsMap.put(uri, concept);
            }
        }

        // store merged concepts within the material object
        List<JSONObject> wikipediaConcepts = new ArrayList<>(conceptsMap.values());
        //System.out.println("unique concepts " + wikipediaConcepts.size());

        /*
        //not relevant anymore with multiple languages
        // get the dominant language of the material
        HashMap<String, Integer> languagesDetected = new HashMap<>();
        for (JSONObject concept : wikipediaConcepts) {
            String lang = concept.getString("lang");
            if (languagesDetected.containsKey(lang)) {
                languagesDetected.put(lang, languagesDetected.get(lang) + 1);
            } else {
                languagesDetected.put(lang, 1);
            }
        }
        String lang = "";
        int maxLang = -1;
        for (String singleLang : languagesDetected.keySet()) {
            if (languagesDetected.get(singleLang) > maxLang) {
                maxLang = languagesDetected.get(singleLang);
                lang = singleLang;
            }
        }
         */
        JSONObject wikifications = new JSONObject();
        wikifications.put("id", id);
        //wikifications.put("language", lang);
        wikifications.put("wiki", wikipediaConcepts);
        //wikifications.put("process", "wikification");
        return wikifications;
    }
}
