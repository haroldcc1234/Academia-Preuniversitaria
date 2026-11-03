package pe.jauja.academia;

import java.nio.file.*;
import java.io.*;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.sync.RequestBody;
import static pe.jauja.academia.Store.*;

@Service
public class FilesService {
    final Path root;
    final S3Client s3;
    final String bucket;

    public FilesService(@Value("${app.storage}") String driver, @Value("${app.files}") String path,
            @Value("${app.s3-endpoint}") String endpoint, @Value("${app.s3-bucket}") String bucket,
            @Value("${app.s3-key}") String key, @Value("${app.s3-secret}") String secret) throws IOException {
        root = Path.of(path).toAbsolutePath().normalize();
        this.bucket = bucket;
        if (driver.equals("s3"))
            s3 = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1).forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret)))
                    .httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder())
                    .build();
        else {
            s3 = null;
            Files.createDirectories(root);
        }
    }

    public String save(byte[] data, String mime) throws IOException {
        String key = id();
        if (s3 == null)
            Files.write(root.resolve(key), data, StandardOpenOption.CREATE_NEW);
        else
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(mime).build(),
                    RequestBody.fromBytes(data));
        return key;
    }

    public byte[] read(String key) throws IOException {
        check(key.matches("[a-f0-9-]{36}"), "Archivo inválido");
        return s3 == null ? Files.readAllBytes(root.resolve(key))
                : s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }
}
