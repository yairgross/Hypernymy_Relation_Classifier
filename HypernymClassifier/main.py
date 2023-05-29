import sys

import numpy as np
from sklearn import svm
from sklearn.decomposition import PCA
from sklearn.metrics import confusion_matrix
from sklearn.model_selection import KFold, cross_val_predict
from nltk.stem import *

#creates a feature vector from the word pairs map, received from the map reduce program.
def create_vector(vec_map, paths_indexes):
    vector_len = len(paths_indexes)
    vector = np.zeros(vector_len)

    vec_map = vec_map[1:-1]
    splitted_map = vec_map.split(" ")
    for split in splitted_map:
        index_occurrence = split.split(":")
        path = index_occurrence[0]
        val = int(index_occurrence[1])
        path_cordinate = np.where(paths_indexes == path)
        vector[path_cordinate] = val
    return np.array(vector)


#creates feature vectors for each example in the sample, according to the output of the map reduce program.
def create_sample(input_path, paths_indexes):
    X = []
    X_words = []
    paths_maps = []
    with open(input_path) as f:
        lines = f.readlines()
        for line in lines:
            splitted_line = line.split("\t")
            w1 = stemm_word(splitted_line[0].split(" ")[0])
            w2 = stemm_word(splitted_line[0].split(" ")[1])
            X_words.append(w1 + " " + w2)
            paths_map = splitted_line[1]
            paths_maps.append(paths_map)
            if paths_map[-1] == "\n":
                paths_map = paths_map[:-1]
            X.append(create_vector(paths_map, paths_indexes))
    return X, X_words,  paths_maps


#creates an array with the dependency paths.
def get_paths(paths_file):
    with open(paths_file) as f:
        content = f.readlines()
        paths = np.array(list(map(lambda line: line.split("\t")[0], content)))
        return paths


def stemm_word(word):
    stemmer = SnowballStemmer(language='english')
    return stemmer.stem(word)

#creates a vector with the label of the sample.
def create_labels(hypernym_path ,X_words):
    y = np.zeros(len(X_words))
    unstemmed_words = ["" for i in range(len(X_words))]
    with open(hypernym_path) as f:
        lines = f.readlines()
        for line in lines:
            words = stemm_word(line.split("\t")[0]) + " " + stemm_word(line.split("\t")[1])
            if words in X_words:
                label = 1 if (line.split("\t")[2] == "True" or line.split("\t")[2] == "True\n") else 0
                y[X_words.index(words)] = label
                unstemmed_words[X_words.index(words)] = line.split("\t")[0] + " " + line.split("\t")[1]
    return y, unstemmed_words


def classify(paths_file, input_path, hypernym_path):
    paths_indexes = get_paths(paths_file) #gets an array with all the paths found in first step of mapreduce
    X, X_words, paths_maps = create_sample(input_path, paths_indexes) #the examples' vectors and their words
    y, unstemmed_words = create_labels(hypernym_path, X_words) #the labels of the examples
    print("Finished creating sample and lables\n")
    # X = np.transpose(np.array(X))
    # X_sparse = csr_matrix(X)
    X_pca = PCA(n_components=0.95).fit_transform(X)

    # indices = np.random.choice(X_pca.shape[0])
    # X_pca= X_pca[indices]
    # y = y[indices]
    # X_words = np.array(X_words)[indices]
    # unstemmed_words = np.array(unstemmed_words)[indices]
    # paths_maps = np.array(paths_maps)[indices]

    #creating a classifier with the examples and labels
    clf = svm.SVC(kernel='linear', C=1).fit(X_pca, y)

    kfold = KFold(n_splits=10, shuffle=True, random_state=0)
    y_preds = cross_val_predict(clf, X_pca, y, cv=kfold)
    tn, fp, fn, tp = confusion_matrix(y, y_preds).ravel()

    precision = tp / (tp+fp)
    recall = tp / (tp+fn)
    f1 = 2 * (precision * recall) / (precision + recall)

    print(f"precision: {precision}")
    print(f"recall: {recall}")
    print(f"f1:{f1}")

    counts = [5, 5, 5, 5]

    for i in range(X_pca.shape[0]):
        label = y[i]
        pred_label = y_preds[i]
        word_pair = unstemmed_words[i]
        path = paths_maps[i]
        if counts[0] != 0 and label == 1 and pred_label == 0:
            print(f"false negative example: {word_pair}, with feature vector: {path}")
            counts[0] -= 1
        elif counts[1] != 0 and label == 0 and pred_label == 0:
            print(f"true negative example: {word_pair}")
            counts[1] -= 1
        elif counts[2] != 0 and label == 0 and pred_label == 1:
            print(f"false positive example: {word_pair}, with feature vector: {path}")
            counts[2] -= 1
        elif counts[3] != 0:
            print(f"true positive example: {word_pair}")
            counts[3] -= 1


if __name__ == '__main__':
    classify(sys.argv[1], sys.argv[2], sys.argv[3])
