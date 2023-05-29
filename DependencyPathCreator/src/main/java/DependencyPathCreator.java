import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;

//import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
//import software.amazon.awssdk.core.ResponseBytes;
//import software.amazon.awssdk.regions.Region;
//import software.amazon.awssdk.services.s3.S3Client;
//import software.amazon.awssdk.services.s3.model.*;

import org.tartarus.snowball.ext.EnglishStemmer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;

public class DependencyPathCreator {
    public static class MapperClass extends Mapper<LongWritable, Text, Text, Text> {
        EnglishStemmer stemmer = new EnglishStemmer();
        List<String> words = new ArrayList<String>();
        String[] nounTags = {"NN", "NNP", "NNS", "NNPS"};
        private S3AFileSystem s3fs;

        @Override
        protected void setup(Mapper<LongWritable, Text, Text, Text>.Context context) throws IOException, InterruptedException {
            super.setup(context);

            String hypernymTxt = getObject("hypernym-bucket", "hypernym.txt", context);
            String[] hypernymLines = hypernymTxt.split("\n");
            for(String line: hypernymLines){
                String[] w1w2Arr = line.split("\\s+");
                words.add(stemWord(w1w2Arr[0]) + " " + stemWord(w1w2Arr[1]));
            }
            System.out.println("hypernyms: " + Arrays.toString(words.toArray()));
        }
        public String getObject(String bucketName, String key, Context context) throws IOException {
            String path = "s3a://"+ bucketName + "/" + key;
            Configuration conf = context.getConfiguration();
            // Configure the S3 filesystem with your AWS credentials and endpoint
            s3fs = new S3AFileSystem();
            try {
                s3fs.initialize(new URI(path), conf);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            Path objectPath = new Path(path);
            FSDataInputStream in = s3fs.open(objectPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            String content = "";
            while ((line = reader.readLine()) != null) {
                content += line + "\n";
            }
            reader.close();
            System.out.println("raw hypernyms: " + content);
            return content;
        }

        protected String stemWord(String word){
            stemmer.setCurrent(word);
            stemmer.stem();
            return stemmer.getCurrent() +"";
        }


        protected WordWithTags parseWordWithTag(String wordStr){
            String word = "";
            String label;
            String posTag;
            int index;
            String[] splitted = wordStr.split("/");
            index = Integer.parseInt(splitted[splitted.length - 1]);
            label = splitted[splitted.length - 2];
            posTag = splitted[splitted.length - 3];
            for(int i = 0; i < splitted.length - 3; i++){
                word += splitted[i];
            }
            word = stemWord(word);
            System.out.println("parsed word with tags: word:" + word  + " part of speech : " + posTag +" label:" + label + " index:" + index);
            return new WordWithTags(word, posTag, label, index);
        }

        protected int indexOfWordInSentence(String w, WordWithTags[] wordsArr){
            int i = 0;
            while( i < wordsArr.length){
                if(wordsArr[i].word.equals(w)) {
                    return i;
                }
                i++;
            }
            return -1;
        }

        protected String findShortestPath(int w1Index, int w2Index, WordWithTags[] wordsArr){
            int len = 10;
            String path = "";
            List<Integer> w1Route = new ArrayList<Integer>();
            List<Integer> w2Route = new ArrayList<Integer>();
            List<Integer> common = new ArrayList<Integer>();
            int nextIndex = w1Index + 1;
            //finds w1s and w2s rout to root
            while(nextIndex != 0){
                w1Route.add(nextIndex);
                common.add(nextIndex);
                nextIndex = wordsArr[nextIndex - 1].index;
            }
            nextIndex = w2Index + 1;
            while(nextIndex != 0){
                w2Route.add(nextIndex);
                nextIndex = wordsArr[nextIndex - 1].index;
            }
            common.retainAll(w2Route); //finds common elements in both routs
            if(common.isEmpty()){
                return "";
            }
            //finds the closest vertex(word) with index c that is common between them
            int minC = -1;
            for(Integer c : common){
                int currLen = w1Route.indexOf(c) + w2Route.indexOf(c);
                if(currLen < len){
                    len = currLen;
                    minC = c;
                }
            }
            //builds dependency path from words and labels
            for(int i = 0 ; i < w1Route.indexOf(minC); i++){
                path += wordsArr[w1Route.get(i) - 1].label + "-";
                if(i != 0){
                    path += wordsArr[w1Route.get(i) - 1].word + "-";
                }
            }
            for(int j = w2Route.indexOf(minC); j >= 0; j--){
                path += wordsArr[w2Route.get(j) - 1].label + "-";
                if(j != 0){
                    path += wordsArr[w2Route.get(j) - 1].word + "-";
                }
            }
            return path.substring(0, path.length() - 1);
        }


        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            System.out.println("map started!");
            String[] splittedValue = value.toString().split("\t");
            String staticNgram = splittedValue[1];
            String occurrences = splittedValue[2];
            System.out.println("map - received ngram: " + staticNgram + "occurrences: "+ occurrences);
            String[] ngramWords = staticNgram.split(" ");
            WordWithTags[] ngramWordWithTags = new WordWithTags[ngramWords.length];
            for(int i =0; i < ngramWordWithTags.length; i++){
                ngramWordWithTags[i] = parseWordWithTag(ngramWords[i]);
            }

            for(int i = 0; i < ngramWordWithTags.length; i++){
                if(Arrays.asList(nounTags).contains(ngramWordWithTags[i].posTag)) {
                    for (int j = i + 1; j < ngramWordWithTags.length; j++) {
                        if(Arrays.asList(nounTags).contains(ngramWordWithTags[j].posTag)) {
                            String path = findShortestPath(i, j, ngramWordWithTags);
                            String w1w2a = ngramWordWithTags[i].word + " " + ngramWordWithTags[j].word;
                            String w1w2b = ngramWordWithTags[j].word + " " + ngramWordWithTags[i].word;
                            
                            if(words.contains(w1w2a)){
                                context.write(new Text(path), new Text(w1w2a + "\t" + occurrences));
                            } else if (words.contains(w1w2b)) {
                                context.write(new Text(path), new Text(w1w2b + "\t" + occurrences));
                            } else{
                                    context.write(new Text(path), new Text("0"));
                                }
                            }
                        }
                    }
                }
            }
        }

    public static class ReducerClass extends Reducer<Text,Text,Text, DependencyPathMap> {
        int dpMin;
        @Override
        protected void setup(Reducer<Text, Text, Text, DependencyPathMap>.Context context) throws IOException, InterruptedException {
            super.setup(context);
            dpMin = context.getConfiguration().getInt("dpMin", 3);
        }

        protected void reduce(Text key, Iterable<Text> values, Reducer<Text,Text,Text, DependencyPathMap>.Context context) throws IOException, InterruptedException {
            System.out.println("reducer started!");
            String path = key.toString();
            HashMap<String, Integer> wordsWithCurrPath = new HashMap<String, Integer>();
            DependencyPathMap mapWritable = new DependencyPathMap();
            for (Text value : values) {
                if (!value.toString().equals("0")) {
                    String[] splittedValue = value.toString().split("\t");
                    int occurrences = Integer.parseInt(splittedValue[1]);
                    String w1w2 = splittedValue[0];
                    System.out.println("reducer - received path: " + path + " words:" + w1w2 + " occurrences: " + occurrences);
                    wordsWithCurrPath.put(w1w2, occurrences);
                }
            }


            for (String w1w2Key : wordsWithCurrPath.keySet()) {
                mapWritable.put(new Text(w1w2Key), new IntWritable(wordsWithCurrPath.get(w1w2Key)));
            }
            if(mapWritable.size() >= dpMin){
                context.write(new Text(path), mapWritable);
            }
        }
    }

    public static class PartitionerClass extends Partitioner<Text, Text> {

        @Override
        public int getPartition(Text key, Text value, int i) {
            return Math.abs(key.toString().hashCode()) % i;
        }
    }


    public static void main(String[] args) throws Exception {


        Configuration conf = new Configuration();

        conf.setInt("dpMin" , Integer.parseInt(args[2])); //args[2] is dpMins value
        Job job = Job.getInstance(conf, "Template");
        job.setJarByClass(DependencyPathCreator.class);

        job.setMapperClass(MapperClass.class);
        job.setPartitionerClass(PartitionerClass.class);
        job.setReducerClass(ReducerClass.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));

        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
