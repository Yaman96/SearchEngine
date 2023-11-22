package searchengine.dto.pageExtractor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.hc.core5.http.ClassicHttpResponse;

import java.io.InputStream;

@Getter
@Setter
@AllArgsConstructor
public class ResponseDto {
    private int responseCode;
    private String content;
}
