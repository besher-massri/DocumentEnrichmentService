# DocumentsAnnotator

This program is used to annotate documents using CoreNLP pipeline.
 
 In full capabilities, text is tokenized into words which are annotated with POS, Lemma(norm). 
 In additionand named entities are extracted from the text.
 
 The program processes files stored in a given directory.
 Each file should contain list of json objects, one per line, each should contain an id and a text fields.
 
 The program will create an output file for each input file, and write the corresponding annotations in it.
 
 
 ## Usage
 At first, you will need to download dependencies, which are specified in maven file. The main dependecies are:
 - json-simple: a library to deal with json objects.
 - stanford-coreNLP-full: the full CoreNLP library from Stanford, which can be downloaded from [here.](https://nlp.stanford.edu/software/stanford-corenlp-full-2018-10-05.zip)
 the file should be extracted in the same directory.
 
 To use the program, modify the configuration in the config file and then run the program.
 
 The configurations are:
 
 - NER [Boolean]: if true, named entity will be extracted, note that this will slow down the process.

 - temporalEntities [Boolean]: if false, SUTime expression recogniser will be turned off, do this when the language isn't English.

 - splitIntoParagraphs [Boolean]: if true, sentence tokenization will be forced to split on the double newlines `\n\n` symbol, which is usually used when splitting between paragraphs. Set this to true if documents are long and you know that double newlines `\n\n` is used in your documents as a paragraph separator.
 
 - synonyms [Boolean]: if true, for each word and named entity, a list of alternative meanings (synonyms) will be provided (if exists). 
 
 - idColumnName [String]: The name of the field which represents the id of the document.
 
 - textColumnName [String]: The name of the field which represents the document text.
 
 - inputDir [String]: the input directory where the files are stored.
 
 - outputDir [String]: the output directory where the annotations files will be stored.
 
 - verbose [0,inf): a number indicating the frequency of outputting the number of documents processed.
 The number indicates how many documents will be processed before outputting a new announcement. Assigning a small value on it might cause a small slowdown in some cases.
 A value of 0 will deactivate it. Note that in all situations, there will be an output sentence for each file.
 
 - writeBatch [0,inf): The number of annotated documents to process before writing on disk. This is used in case that writing
 on disk is the bottleneck. Lower value means more time, higher value means more memory usage. A good starting value
 is 100. A value of 0 will make writing to disk once per file. 
 
 - fileFrom [0,inf): the index of the file in the directory to start the process from. This is used especially for running multiple instances of the program in parallel.
 
 - fileTo [0,inf): the index of the file in the directory to end the process at (inclusive). This is used especially for running multiple instances of the program in parallel.

## Annotation format
Each annotation will have the following attributes:
- id: the given id of the document. In case that `splitIntoParagraph` parameter is true,
the id of each paragraph annotation will be: `documentId_XXX`
where XXX three digit number representing the paragraph index w.r.t the text.
- words: a list of annotated words that resulted from tokenizing the text.
- spaces: the spaces between the tokens, s.t. spaces[0]+word[0].word+spaces[1]+...+word[n-1].word+spaces[n] = text.
- annotations: list of named entities extracted from the text.



Each annotated word is an object that has the following attributes:

- word: the literal word in the original form.
- token: the word in tokenized form. In most cases it will be the same as the original word.
However, special characters like quotes and parathesis will be have different representation.
- norm: the norm form (lemma).
- pos: part of speech tags. The full list can be found [here.](https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html)
- ner: named entity recognition that this word belong to.
- iFrom: the index of the <strong>character</strong> that represent the start of the word.
- iTo: the index of the <strong>character</strong> that represent the end of the word.


Each named entity is an object that has the following attributes:

- text: named entity as mentioned in the text.
- type: the type of named entity. The named entity supported are (12 classes):
  - named entities (PERSON, LOCATION, ORGANIZATION, MISC).
  - numerical entities (MONEY, NUMBER, ORDINAL, PERCENT).
  - temporal entities (DATE, TIME, DURATION, SET).
  
- iFrom: the index of the <strong>character</strong> that represent the **start** of the entity.
- iTo: the index of the <strong>character</strong> that represent the **end** of the entity.
- wFrom: the index of the <strong>word</strong> that represent the **start** of the entity.
- wTo: the index of the <strong>word</strong> that represent the **end** of the entity.

An example of the annotation file can be found in `example/` directory, which resulted from running the text provided with the config specified in the config file.

## Acknowledgments
This work is developed by [AILab](http://ailab.ijs.si/) at [Jozef Stefan Institute](https://www.ijs.si/).

The work is supported by the [EnviroLens project](https://envirolens.eu/),
a project that demonstrates and promotes the use of Earth observation as direct evidence for environmental law enforcement, including in a court of law and in related contractual negotiations.
