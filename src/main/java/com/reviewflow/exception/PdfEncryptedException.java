package com.reviewflow.exception;

public class PdfEncryptedException extends RuntimeException {
    private final String code;

    public PdfEncryptedException() {
        super("Password-protected PDFs are not accepted. Please remove the password before submitting");
        this.code = "PDF_ENCRYPTED";
    }

    public String getCode() {
        return code;
    }
}
