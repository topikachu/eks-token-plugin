package io.jenkins.plugins.eks_token_plugin;

import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.CredentialsConvertionException;
import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.SecretToCredentialConverter;
import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.SecretUtils;
import com.cloudbees.plugins.credentials.CredentialsScope;
import io.fabric8.kubernetes.api.model.Secret;
import org.jenkinsci.plugins.variant.OptionalExtension;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@OptionalExtension(requirePlugins = {"kubernetes-credentials-provider"})
public class EksTokenCredentialsConvertor extends SecretToCredentialConverter {
    private static final Logger LOG = Logger.getLogger(EksTokenCredentialsConvertor.class.getName());

    @Override
    public boolean canConvert(String type) {
        return "eks".equals(type);
    }

    @Override
    public EksTokenCredentials convert(Secret secret) throws CredentialsConvertionException {

        SecretUtils.requireNonNull(secret.getData(), "eks credential definition contains no data");

        String clusterNameInBase64 = SecretUtils.getNonNullSecretData(secret, "clusterName", "eks credential is missing clusterName");
        String clusterName = decodeBase64(clusterNameInBase64, "Not a valid clusterName");


        String regionInBase64 = SecretUtils.getNonNullSecretData(secret, "region", "eks credential is missing region");
        String region = decodeBase64(regionInBase64, "Not a valid region");


        EksTokenCredentials eksTokenCredentials = new EksTokenCredentials(CredentialsScope.GLOBAL,
                // ID
                SecretUtils.getCredentialId(secret),
                // Description
                SecretUtils.getCredentialDescription(secret),
                clusterName, region
        );

        Optional<String> awsCredentialsId = SecretUtils.getOptionalSecretData(
                secret,
                "awsCredentialsId",
                "eks credential : failed to retrieve optional parameter awsCredentialsId");
        awsCredentialsId
                .map(awsCredentialsIdInBase64 -> {
                    try {
                        return decodeBase64(awsCredentialsIdInBase64, "Not a valid awsCredentialsId");
                    } catch (CredentialsConvertionException e) {
                        throw new RuntimeException(e);
                    }
                })
                .ifPresent(eksTokenCredentials::setAwsCredentialsId);

        return eksTokenCredentials;
    }


    private String decodeBase64(String base64, String errorMessage) throws CredentialsConvertionException {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes != null) {
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                decoder.onMalformedInput(CodingErrorAction.REPORT);
                decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                CharBuffer decode = decoder.decode(ByteBuffer.wrap(bytes));
                return SecretUtils.requireNonNull(decode.toString(), errorMessage);
            } else {
                throw new CredentialsConvertionException(errorMessage);
            }

        } catch (CharacterCodingException e) {
            LOG.log(Level.WARNING, "failed to decode Secret, is the format valid? {0} {1}", new String[]{base64, e.getMessage()});
            throw new CredentialsConvertionException(errorMessage, e);
        }
    }
}
