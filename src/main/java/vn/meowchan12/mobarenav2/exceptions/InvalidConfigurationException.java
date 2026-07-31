package vn.meowchan12.mobarenav2.exceptions;

public class InvalidConfigurationException extends Exception {
    private final String fileName;
    private final String section;

    public InvalidConfigurationException(String message, String fileName, String section) {
        super(message);
        this.fileName = fileName;
        this.section = section;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSection() {
        return section;
    }
}