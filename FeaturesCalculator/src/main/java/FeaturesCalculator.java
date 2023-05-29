import org.apache.hadoop.conf.Configuration;
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
import java.util.HashMap;
import java.util.Map;

public class FeaturesCalculator {
    public static class MapperClass extends Mapper<LongWritable, Text, Text, Text> {

        @Override
        protected void map(LongWritable key, Text value, Mapper<LongWritable, Text, Text, Text>.Context context) throws IOException, InterruptedException {

            String[] splittedValue = value.toString().split("\t");
            String path = splittedValue[0];
            String mapStr = splittedValue[1];
            if(!mapStr.equals("{}")){
                Map<String, Integer> parsedMap = parseMap(mapStr);
                for(String w1w2: parsedMap.keySet()){
                    context.write(new Text(w1w2), new Text(path + " " + parsedMap.get(w1w2)));
                }
            }

        }
        protected Map<String, Integer> parseMap(String mapStr){
            Map<String, Integer> map = new HashMap<String, Integer>();
            mapStr = mapStr.substring(1, mapStr.length() - 1);
            String[] mapSplitted = mapStr.split(",");
            for(String split : mapSplitted){
                String[] keyAndVal = split.split(":");
                map.put(keyAndVal[0], Integer.parseInt(keyAndVal[1]));
            }
            return map;
        }
    }
    public static class ReducerClass extends Reducer<Text,Text,Text, DependencyPathMap> {
        @Override
        protected void reduce(Text key, Iterable<Text> values, Reducer<Text, Text, Text, DependencyPathMap>.Context context) throws IOException, InterruptedException {
            DependencyPathMap map = new DependencyPathMap();
            String w1w2 = key.toString();

            for(Text value: values){
                String[] splittedVal = value.toString().split(" ");
                String path = splittedVal[0];
                int occurrence = Integer.parseInt(splittedVal[1]);
                map.put(new Text(path), new IntWritable(occurrence));
            }
            context.write(new Text(w1w2), map);
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

        Job job = Job.getInstance(conf, "Template");
        job.setJarByClass(FeaturesCalculator.class);

        job.setMapperClass(MapperClass.class);
        job.setPartitionerClass(PartitionerClass.class);
        job.setReducerClass(ReducerClass.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DependencyPathMap.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }

}
