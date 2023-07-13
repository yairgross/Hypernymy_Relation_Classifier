Submitters:
	Sharon Hendy 209467158
	Yair Gross 314625781

Our project is composed of 2 Map-Reduct programs, and a Python program:
1. DependencyPathCreator (Map-Reduce program #1):
	- The Map function: 
		* Reads a line from the static n-gram corpus.
		* Creates a dependency path for each noun pair of words in the n-gram, after stemming the nouns.
		* If the n-gram contains a word pair from "hypernym.txt", writes to context the key-value pair <dependencyPath>,(<word pair> <occurrence>), where occurrence is the occurrence of the specific static n-gram in the corpus.
		* Else, writes to the context the key-value pair <dependencyPath>,"0".
		* The number of different keys sent is the amount of different dependency paths between 2 noun words.
	- The Reduce function:
		* For every dependency path:
			- Aggregates all word pairs and their occurrences inside a HashMap.
			- If the number of nouns pairs stored in the HashMap is greater or equal to DPMin, writes to the context the key-value pair <dependencyPath>,<HashMap>.
			- The number of keys and values sent is the number of different dependency paths.
	- Memory usage:
		* The Mapper stores all word pairs from the file "hypernym.txt".
		* The Reducer stores all the word pairs and their occurrences, for a specific dependency path (one at a time).
1. FeaturesCalculator (Map-Reduce program #2):
	- The Map function:
		* Revices a key-value pair <dependencyPath>,<HashMap> which was created by the DependencyPathCreator.
		* For each pair <word pair>, <occurrence> inside the HashMap:
		  Writes to the context the key-value pair <word pair>, (<path> <occurrence>).
		* The number of different keys sent is the number of different word pairs.
	- The Reduce function:
		* For every word pair, aggregates all the dependency paths and their occurrences inside a HashMap, and writes to the context the key-value pair <word pair>, <HashMap>.
		* The number of different keys sent is the number of different word pairs from the corpus which appear in the file "hypernym.txt".
3 HypernymClassifier (Python program):
	- Get all dependency paths from the output of the DependencyPathCreator program, in order to determine the number of features in each vector in the sample.
	- Builds a sample composed of vectors which are created according to the output of the FeaturesCalculator program.
	- Creates a vector of lables for the sample according to the "True" / "False" value in the "hypernym.txt" file.
	- Runs 10-fold cross validation on the dataset, and prints the precision, recall and f1 score. 
	

Communication:
1. DependencyPathCreator:
	- Number of key-value pairs sent from the Mappers to the Reducers: 4,854,804.
	- Number of bytes sent from the Mappers to the Reducers: 94,543,515. 
	- Number of key-value pairs sent from the Reducers to the context:	3,537.
2. FeaturesCalculator:
	- Number of key-value pairs sent from the Mappers to the Reducers: 38,558.
	- Number of bytes sent from the Mappers: 1,350,316
	- Number of key-value pairs sent from the Reducers to the context:	11,931.

Results:
	- Precision: 0.7195979899497488
	- Recall: 0.9937543372657877
	- F1 score: 0.8347420577091228

Analysis:
	- True positive examples:
		* 4 figure
		* 8 figure
		* abdomen stomach
		* germany country
		* girl daughter
	- True negative examples:
		* derivation name
		* stage patient
		* view b
		* ability earnings
		* guy couple
	- False positive examples:
		* abdomen bone, with feature vector: {pobj-abdomen-conj:10}
		* ability johnson, with feature vector: {amod-nsubj:11 nn-pobj:10 dobj-abil-prep-of-pobj:11}
		* gene therapy, with feature vector: {nn-pobj-a-nn:18 nn-dep:99 nn-appos:20 nn-pobj:13 nn-pobj-a.-nn:23 nn-prep:11}
		* glass nature, with feature vector: {nsubj-ROOT-'s-attr:22}
		* sort treatment, with feature vector: {attr-sort-prep-of-pobj:55}
		Explanation: We belive that our classifier made a mistake labeling these examples "true" because from the features vectors we observe that they appear in a sentence which indicates usually a hypernym connection.
		For example: in the features vector of the pair "glass nature", there is an "s" which indicates a possessive form (שייכות).
	- False negative examples:
		* man earth, with feature vector: {attr-man-prep-on-pobj:14 nsubj-ROOT-abandon-prep-on-pobj:14 nsubj-man-prep-on-pobj:64 nsubj-ROOT-'s-prep-on-pobj:17 dep-pobj-'s-prep-on-pobj:77 nsubj-ROOT-'s-prep-like-pobj:12}
		* mister name, with feature vector: {attr-name-appos:96 attr-ROOT-'s-attr:232 nn-attr:18 nsubj-ROOT-'s-attr:14}
		* peptide part, with feature vector: {nn-dep-a-dep:195}
		* smell ability, with feature vector: {nsubj-abil-infmod-see-conj:25 nn-dep:43 nn-pobj:13 pobj-abil-conj:22 nn-conj:27 dobj-abil-infmod-see-conj:52 pobj-abil-infmod-see-conj:11 pobj-abil-infmod-recogn-prep-by-pobj:10 dobj-abil-infmod-detect-dobj:12 nn-appos:13 nsubj-abil-infmod-identifi-prep-by-pobj:11 dobj-abil-infmod-identifi-prep-by-pobj:13 nn-ROOT:13}
		* world men, with feature vector: {nsubj-xcomp-abandon-dobj:11 attr-ROOT-'s-prep-for-pobj:13 nn-dep:17 pobj-prep-in-ROOT-abound-nsubj:18 dep-ROOT-'s-conj:14 pobj-men-prep-of-pobj:14 nsubj-parataxis-'s-conj:331 nsubj-ROOT-'s-attr:24 attr-world-prep-of-pobj:20 dobj-ROOT-abandon-prep-to-pobj:27 nsubj-dep:11 nsubj-ccomp-'s-conj:657 nsubj-ROOT-'s-conj:1466 pobj-men-prep-in-pobj:20}
		Explanation: We can see that these word pairs mostly appear in sentences which don't indicate a hypernym connection, therefore the classifier made a wrong classification.
		For example, the word "smell" in the pair "smell ability" can appear as a word decribing a certain smell (ריח) and not as the verb (להריח). From the features vector, we can see that the sentences in the corpus containing the pair did not indicate a hypernym connection.
		
