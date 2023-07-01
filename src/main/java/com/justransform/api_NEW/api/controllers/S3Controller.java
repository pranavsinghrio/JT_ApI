package com.justransform.api_NEW.api.controllers;

import com.justransform.api_NEW.model.S3PostData;
import com.justransform.api_NEW.model.S3ResponseData;
import com.justransform.app.base.services.ResourceService;
import com.justransform.utils.PropertyReaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Random;

@RestController()
@CrossOrigin(origins = {
        "https://demoapps.justransform.com",
        "https://uatapps.justransform.com",
        "https://apps.justransform.com",
        "https://one.jtb2b.net/",
        "https://uatone.jtb2b.net/"
})
@RequestMapping("/storage/s3")
public class S3Controller {

    @Autowired
    private ResourceService resourceService;

    @GetMapping
    public ResponseEntity<S3ResponseData> get(@RequestParam("key") String key) {
        S3ResponseData response = new S3ResponseData();
        String baseUrl = PropertyReaderUtil.getInstance().getPropertyValue("formIO.uri");
        response.setUrl(baseUrl + "/storage/s3/download?key=" + key);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{key}/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String key, @PathVariable String fileName) throws IOException {

        InputStream inputStream = JTUtil.downloadFile(key);

        InputStreamResource resource = new InputStreamResource(inputStream);
        String headerValue = "attachment; filename=\"" + fileName + "\"";

        return ResponseEntity.ok()
                .contentLength(inputStream.available())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .body(resource);
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam("key") String key) throws IOException {

        String[] info = key.split("/");
        InputStream inputStream = JTUtil.downloadFile(info[0]);

        InputStreamResource resource = new InputStreamResource(inputStream);
        String headerValue = "attachment; filename=\"" + info[1] + "\"";

        return ResponseEntity.ok()
                .contentLength(inputStream.available())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .body(resource);

    }

    @PostMapping
    public ResponseEntity<S3ResponseData> upload(@RequestBody S3PostData data) {
        S3ResponseData response = new S3ResponseData();
        String baseUrl = PropertyReaderUtil.getInstance().getPropertyValue("formIO.uri");
        response.setUrl(baseUrl + "/storage/s3");

        Random random = new Random(System.nanoTime());
        String resourceId = String.valueOf(random.nextLong());
        String md5Str = resourceId + "" +System.nanoTime() + FileUtil.getRandom(3);
        byte[] bytesOfMessage = md5Str.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getEncoder();
        String md5 = encoder.encodeToString(bytesOfMessage);
        String key = md5 + "_" + resourceId;

        response.setData(new HashMap<>());
        response.getData().put("key", key);

        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<Void> upload(
            @RequestPart("key") String key,
            @RequestPart("fileName") String fileName,
            @RequestPart("file") MultipartFile file
    ) throws IOException, JTServerException {
        resourceService.uploadFileToS3(key.split("/")[0], fileName, file.getContentType(),
                file.getInputStream());

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}