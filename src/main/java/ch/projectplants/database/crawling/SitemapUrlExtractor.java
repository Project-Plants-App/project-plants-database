package ch.projectplants.database.crawling;

import ch.projectplants.database.crawling.siteindex.Sitemapindex;
import ch.projectplants.database.crawling.siteindex.TSitemap;
import ch.projectplants.database.crawling.sitemap.ObjectFactory;
import ch.projectplants.database.crawling.sitemap.TUrl;
import ch.projectplants.database.crawling.sitemap.Urlset;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

public class SitemapUrlExtractor {

    private static final String BASE_SITEMAP = "sitemap.xml";

    private final Unmarshaller sitemapUnmarshaller = createSitemapUnmarshaller();

    public Set<URL> fetchContentUrls(String baseUrl) {
        return fetchContentUrls(createUrl(baseUrl, BASE_SITEMAP));
    }

    private Set<URL> fetchContentUrls(URL sitemapUrl) {
        try {
            Set<URL> contentUrls = new HashSet<>();

            var result = fetchUrl(sitemapUrl);
            if (result == null) {
                return contentUrls;
            }

            if (result instanceof Sitemapindex sitemapindex) {
                for (TSitemap sitemapEntry : sitemapindex.getSitemap()) {
                    URL sitemapEntryUrl = createUrl(sitemapEntry.getLoc());

                    contentUrls.addAll(fetchContentUrls(sitemapEntryUrl));
                }
            } else if (result instanceof Urlset sitemap) {
                for (TUrl contentEntry : sitemap.getUrl()) {
                    URL contentEntryUrl = createUrl(contentEntry.getLoc());

                    contentUrls.add(contentEntryUrl);
                }
            } else {
                throw new IllegalStateException();
            }

            return contentUrls;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object fetchUrl(URL sitemapUrl) throws JAXBException, URISyntaxException, IOException, InterruptedException {
        var httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        var request = HttpRequest.newBuilder(sitemapUrl.toURI())
                .header("Accept", "application/xml,text/xml")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            return null;
        }

        try (InputStream inputStream = response.body()) {
            return sitemapUnmarshaller.unmarshal(inputStream);
        }
    }

    private Unmarshaller createSitemapUnmarshaller() {
        try {
            return JAXBContext.newInstance(
                    ObjectFactory.class,
                    ch.projectplants.database.crawling.siteindex.ObjectFactory.class
            ).createUnmarshaller();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static URL createUrl(String... parts) {
        try {
            return new URL(String.join("/", parts));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

}
