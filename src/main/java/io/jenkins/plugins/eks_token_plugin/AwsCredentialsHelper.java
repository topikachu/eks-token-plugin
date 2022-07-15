package io.jenkins.plugins.eks_token_plugin;

import com.amazonaws.auth.AWSCredentials;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import java.util.Collections;
import java.util.Optional;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

public class AwsCredentialsHelper {
    static public Optional<AWSCredentials> getAWSCredentials(String credentialsId) {

        return lookupCredentials(AmazonWebServicesCredentials.class,
                Jenkins.getInstanceOrNull(), ACL.SYSTEM, Collections.emptyList())
                .stream()
                .filter(CredentialsMatchers.withId(credentialsId)::matches)
                .map(AmazonWebServicesCredentials::getCredentials)
                .findFirst();

    }
}
