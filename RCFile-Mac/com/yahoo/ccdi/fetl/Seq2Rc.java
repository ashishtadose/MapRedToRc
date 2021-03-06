package com.yahoo.ccdi.fetl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.io.RCFile;
import org.apache.hadoop.hive.ql.io.RCFileOutputFormat;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.lib.IdentityReducer;

public class Seq2Rc {
  
  public static final int COLUMN_NUMBER = 800;
  
  public static class Seq2RcMapper extends MapReduceBase implements
      Mapper<ETLKey, ETLValue, LongWritable, ManualETLValue> {
    static int count = 0;

    @Override
    public void map(ETLKey key, ETLValue value, OutputCollector output,
        Reporter reporter) throws IOException {
        System.out.println("### Seq2RC: number of records "
            + count++);
        ManualETLValue mValue = new ManualETLValue(value.getFilterTag(),
            value.getDhrTag(), value.getTransformErrorTag(),
            value.getSimpleFields(), value.getMapFields(),
            value.getMapListFields());
        output.collect(new LongWritable(1), mValue);
    }
  }
  
  /**
   * A reducer class that just emits the sum of the input values.
   */

  public static class Reduce extends MapReduceBase
    implements Reducer<LongWritable, ManualETLValue, LongWritable, ManualETLValue> {
    
    public void reduce(LongWritable key, Iterator<ManualETLValue> mValue,
                       OutputCollector<LongWritable, ManualETLValue> output, 
                       Reporter reporter) throws IOException {
      while (mValue.hasNext()) {
      output.collect(key, mValue.next());
      }
    }
  }

  public static class NonSplitableSequenceFileInputFormat extends
      SequenceFileInputFormat {
    protected boolean isSplitable(FileSystem fs, Path filename) {
      return false;
    }
  }
  
    public static void main(String[] args) throws Exception {        
        Configuration conf = new Configuration();
            
        JobConf aBF1RCPrint = new JobConf(Seq2Rc.class);
        
        aBF1RCPrint.setJobName(conf.get("mapred.job.name", "Seq2Rc"));
        aBF1RCPrint.setMapperClass(Seq2RcMapper.class);
        aBF1RCPrint.setReducerClass(Reduce.class);
        
        //aBF1RCPrint.setInputFormat(SequenceFileInputFormat.class);
        aBF1RCPrint.setInputFormat(NonSplitableSequenceFileInputFormat.class);
        aBF1RCPrint.setOutputFormat(RCFileOutputFormat.class);
        
        RCFileOutputFormat.setCompressOutput(aBF1RCPrint, true);
        RCFileOutputFormat.setColumnNumber(aBF1RCPrint, COLUMN_NUMBER);
        
        aBF1RCPrint.setMapOutputKeyClass(LongWritable.class);
        aBF1RCPrint.setMapOutputValueClass(ManualETLValue.class);
        
        aBF1RCPrint.setOutputKeyClass(LongWritable.class);
        aBF1RCPrint.setOutputValueClass(ManualETLValue.class);
        
        aBF1RCPrint.setCompressMapOutput(true);
        
        aBF1RCPrint.set("mapred.job.queue.name", "audience_fetl");
        
        // set compression
        aBF1RCPrint.set("mapred.output.compress", "true");
        aBF1RCPrint.set("mapred.output.compression.type", "BLOCK");
        aBF1RCPrint.set("mapred.output.compression.codec", "org.apache.hadoop.io.compress.GzipCodec");
        
//        aBF1RCPrint.setInt("io.seqfile.compress.blocksize", 128*1024*1024); // 32 M
//        aBF1RCPrint.setInt("hive.io.rcfile.record.buffer.size", 128*1024*1024); //32 M
        
        // old style of processing arguments
//        String[] otherArgs = new GenericOptionsParser(conf,args).getRemainingArgs();
//        if(otherArgs.length != 3) {
//            aBF1RCPrint.setNumReduceTasks(0);
//        } else {
//            System.out.println(otherArgs[2]);
//            aBF1RCPrint.setNumReduceTasks(Integer.parseInt(otherArgs[2]));
//        }
//      FileInputFormat.setInputPaths(aBF1RCPrint, otherArgs[0]);
//      FileOutputFormat.setOutputPath(aBF1RCPrint, new Path(otherArgs[1]));
        
        // new style of processing arguments
        List<String> other_args = new ArrayList<String>();
        for(int i=0; i < args.length; ++i) {

          try {
            if ("-m".equals(args[i])) {
              aBF1RCPrint.setNumMapTasks(Integer.parseInt(args[++i]));
            } else if ("-r".equals(args[i])) {
              aBF1RCPrint.setNumReduceTasks(Integer.parseInt(args[++i]));
            } 
            else if ("-cblock".equals(args[i])) {
              //io.seqfile.compress.blocksize
              aBF1RCPrint.setInt("io.seqfile.compress.blocksize", Integer.parseInt(args[++i])); // 32 M
            } else if ("-raw".equals(args[i])) {
              //aBF1RCPrint.setInt(RCFile.Writer.COLUMNS_BUFFER_SIZE_CONF_STR, Integer.parseInt(args[++i])); //32 M
              aBF1RCPrint.setInt("hive.io.rcfile.record.buffer.size", Integer.parseInt(args[++i]));
            } else if ("-splitsize".equals(args[i])) {
              aBF1RCPrint.setInt("mapred.min.split.size", Integer.parseInt(args[++i])); // 2 G
            } 
            else {
              // by default, 0 reducer
              aBF1RCPrint.setNumReduceTasks(1);
              other_args.add(args[i]);
            }
          } catch (NumberFormatException except) {
            System.out.println("ERROR: Integer expected instead of " + args[i]);
            //printUsage();
            return;
          } catch (ArrayIndexOutOfBoundsException except) {
            System.out.println("ERROR: Required parameter missing from " +
                               args[i-1]);
            //printUsage();
            return;
          }
        }
        FileInputFormat.setInputPaths(aBF1RCPrint, other_args.get(0));
        FileOutputFormat.setOutputPath(aBF1RCPrint, new Path(other_args.get(1)));
//  
        RunningJob myPrinter = JobClient.runJob(aBF1RCPrint);
    }
  }

