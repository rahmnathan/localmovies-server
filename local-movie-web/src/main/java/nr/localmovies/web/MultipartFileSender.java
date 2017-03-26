package nr.localmovies.web;

import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
class MultipartFileSender {

    private static final int DEFAULT_BUFFER_SIZE = 8000;

    void serveResource(Path filepath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response == null || request == null) {
            return;
        }

        Long length = Files.size(filepath);
        Range full = new Range(0, length - 1, length);
        String range = request.getHeader("Range");
        InputStream input = new BufferedInputStream(Files.newInputStream(filepath));
        OutputStream output = response.getOutputStream();
        if(range == null) {
            System.out.println("null range");
            response.setHeader("Content-Range", "bytes " + full.start + "-" + full.end + "/" + full.total);
            response.setHeader("Content-Length", String.valueOf(full.length));
            Range.copy(input, output, length, full.start, full.length);
        } else {
            long start = Long.valueOf(range.substring(6, range.length()-1));
            Range range1 = new Range(start, length - 1, length);
            System.out.println(start);
            response.setHeader("Content-Range", "bytes " + range1.start + "-" + range1.end + "/" + range1.total);
            response.setHeader("Content-Length", String.valueOf(range1.length));
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            Range.copy(input, output, length, range1.start, range1.length);
        }
    }

    private static class Range {
        final long start;
        final long end;
        final long length;
        final long total;

        /**
         * Construct a byte range.
         * @param start Start of the byte range.
         * @param end End of the byte range.
         * @param total Total length of the byte source.
         */
        Range(long start, long end, long total) {
            this.start = start;
            this.end = end;
            this.length = end - start + 1;
            this.total = total;
        }

        private static void copy(InputStream input, OutputStream output, long inputSize, long start, long length) throws IOException {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int read;

            if (inputSize == length) {
                // Write full range.
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } else {
                input.skip(start);
                long toRead = length;

                while ((read = input.read(buffer)) > 0) {
                    if ((toRead -= read) > 0) {
                        output.write(buffer);
                        output.flush();
                    } else {
                        output.write(buffer);
                        output.flush();
                        break;
                    }
                }
            }
        }
    }
}