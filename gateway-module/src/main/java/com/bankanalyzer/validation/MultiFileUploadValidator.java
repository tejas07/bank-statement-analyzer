package com.bankanalyzer.validation;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Component
public class MultiFileUploadValidator implements Validator<List<MultipartFile>> {

    private static final int MAX_FILES = 10;

    @Override
    public void validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files uploaded.");
        }
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Maximum " + MAX_FILES + " files per request.");
        }
    }
}
