import org.apache.hadoop.mapreduce.lib.input.LineRecordReader;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;

import java.io.IOException;

public class RecordReaderFeaturesCalc extends RecordReader<LongWritable, Text> {
    protected LineRecordReader lineRecordReader;
    private LongWritable lineNumber;

        public RecordReaderFeaturesCalc() {
            super();
            lineNumber = new LongWritable(-1);
            lineRecordReader = new LineRecordReader();
        }

    @Override
    public void initialize(InputSplit inputSplit, TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
        lineRecordReader.initialize(inputSplit, taskAttemptContext);
    }

    @Override
        public boolean nextKeyValue() throws IOException {
            boolean success = lineRecordReader.nextKeyValue();
            if (success) {
                lineNumber.set(lineNumber.get() + 1);
            }
            return success;
        }


        @Override
        public LongWritable getCurrentKey() {
            return lineNumber;
        }

        @Override
        public Text getCurrentValue() {
            return lineRecordReader.getCurrentValue();
        }

    @Override
    public float getProgress() throws IOException, InterruptedException {
        return lineRecordReader.getProgress();
    }

    @Override
    public void close() throws IOException {
        lineRecordReader.close();
    }
}


