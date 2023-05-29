import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;
import com.amazonaws.services.elasticmapreduce.model.*;

import java.io.File;
import java.io.IOException;

public class main {
    public static void main(String[] args) throws IOException {
        String bucketName = args[0];
        String DepJarPath = "s3n://" + bucketName + "/DependencyPathCreator-jar-with-dependencies.jar";
        String FeaJarPath = "s3n://" + bucketName + "/FeaturesCalculator-jar-with-dependencies.jar";
        String outputDep = "s3n://" + bucketName + "/outputDep";
        String outputFea = "s3n://" + bucketName + "/outputFea";
        String logs = "s3n://" + bucketName + "/logs/";

        AWSCredentials credentials_profile = null;
        try {
            credentials_profile = new ProfileCredentialsProvider("default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load credentials from .aws/credentials file. " +
                            "Make sure that the credentials file exists and the profile name is specified within it.",
                    e);
        }
        AmazonElasticMapReduce mapReduce = new AmazonElasticMapReduceClient(credentials_profile);

        HadoopJarStepConfig hadoopJarStep = new HadoopJarStepConfig()
                .withJar(DepJarPath) // This should be a full map reduce application.withMainClass("some.pack.MainClass")
                .withArgs("s3n://hypernym-bucket/biarcs.00-of-99", outputDep, args[1]); //dpMin is second argument

        StepConfig stepConfig = new StepConfig()
                .withName("DependencyPathCreator_step")
                .withHadoopJarStep(hadoopJarStep)
                .withActionOnFailure("TERMINATE_JOB_FLOW");

        HadoopJarStepConfig hadoopJarStep1 = new HadoopJarStepConfig()
                .withJar(FeaJarPath) // This should be a full map reduce application.withMainClass("some.pack.MainClass")
                .withArgs(outputDep, outputFea);

        StepConfig stepConfig1 = new StepConfig()
                .withName("FeaturesCalculator_step")
                .withHadoopJarStep(hadoopJarStep1)
                .withActionOnFailure("TERMINATE_JOB_FLOW");


        StepConfig[] steps = {stepConfig, stepConfig1};

        JobFlowInstancesConfig instances = new JobFlowInstancesConfig()
                .withInstanceCount(8)
                .withMasterInstanceType(InstanceType.M4Large.toString())
                .withSlaveInstanceType(InstanceType.M4Large.toString())
                .withHadoopVersion("2.6.0").withEc2KeyName("vockey")
                .withKeepJobFlowAliveWhenNoSteps(false)
                .withPlacement(new PlacementType("us-east-1a"));

        RunJobFlowRequest runFlowRequest = new RunJobFlowRequest()
                .withName("VectorsCreator_job")
                .withInstances(instances)
                .withSteps(steps)
                .withServiceRole("EMR_DefaultRole") //needed for permissions
                .withJobFlowRole("EMR_EC2_DefaultRole") //needed for permissions
                .withReleaseLabel("emr-4.0.0") //this is the compatible version with hadoop 2.6.0
                .withLogUri(logs);
        RunJobFlowResult runJobFlowResult = mapReduce.runJobFlow(runFlowRequest);
        String jobFlowId = runJobFlowResult.getJobFlowId();
        System.out.println("Ran job flow with id: " + jobFlowId);

    }
}
