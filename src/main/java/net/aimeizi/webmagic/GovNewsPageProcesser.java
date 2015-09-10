package net.aimeizi.webmagic;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.aimeizi.jest.client.elasticsearch.TransportClient;
import net.aimeizi.model.Article;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 爬取中央政府网要闻、热点、部门新闻、地方报道、执法监管等新闻信息
 * Created by Administrator on 2015/9/10.
 */
public class GovNewsPageProcesser implements PageProcessor {

    // 新闻列表
    private final static String URL_LIST = "http://new\\.sousuo\\.gov\\.cn\\/column\\/\\d+\\/\\d+\\.htm";

    // 新闻列表页正文url
    private final static String URL_POST = "http://www\\.gov\\.cn/[\\w/-]+\\.htm";

    private Site site = Site.me()
            .setRetryTimes(3)
            .setSleepTime(1000)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.134 Safari/537.36");

    public void process(Page page) {
        // 列表页
        if(page.getUrl().regex(URL_LIST).match()){
            // 添加详情页请求链接
            page.addTargetRequests(page.getHtml().links().regex(URL_POST).all());
            // 添加列表页请求链接
            page.addTargetRequests(page.getHtml().links().regex(URL_LIST).all());
        }else{// 详情页
            page.putField("title", page.getHtml().xpath("//div[@class='pages-title']").toString());
            page.putField("content", page.getHtml().xpath("//div[@class='article-colum']/div[@class='pages_content']/table[@id='printContent']/tbody/tr/td").toString());
            page.putField("source",page.getHtml().xpath("//div[@class='article-colum']/div[@class='pages-date']/span[@class='font'][2]").toString().replace("来源： ", ""));
            page.putField("author",page.getHtml().xpath("//div[@class='article-colum']/div[@class='pages_content']/div[@class='editor']").toString().replace("责任编辑： ", ""));
            page.putField("create",page.getHtml().xpath("//div[@class='article-colum']/div[@class='pages-date']").toString());
            page.putField("url",page.getUrl().get());
            // 索引article
            Article article = new Article();
            article.setTitle(replaceHTML((String)page.getResultItems().get("title")));
            article.setContent(replaceHTML((String) page.getResultItems().get("content")));
            article.setSource(replaceHTML((String) page.getResultItems().get("source")));
            article.setAuthor(replaceHTML((String) page.getResultItems().get("author")));
            try {
                String dataStr = replaceHTML((String) page.getResultItems().get("create"));
                Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
                Matcher matcher = pattern.matcher(dataStr);
                if(matcher.find()){
                    dataStr = matcher.group(0);
                }
                article.setPubdate(new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(dataStr));
            }catch (Exception e){
            }
            article.setUrl(page.getUrl().get());

            // 创建索引
            ObjectMapper mapper = new ObjectMapper();
            try {
                TransportClient.createIndex("news","article",mapper.writeValueAsString(article));
            }catch (Exception e){
            }

        }
    }

    public Site getSite() {
        return site;
    }

    /**
     * html字符过滤
     * @param str
     * @return
     */
    private static String replaceHTML(String str){
        return str.replaceAll("\\<.*?>","").replaceAll("&nbsp;","");
    }

    public static void main(String[] args) {

        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext.xml");
        JdbcPipeline jdbcPipeline = (JdbcPipeline)applicationContext.getBean("jdbcPipeline");
        Spider.create(new GovNewsPageProcesser())
                .addUrl("http://new.sousuo.gov.cn/column/19769/0.htm") //要闻
                .addUrl("http://new.sousuo.gov.cn/column/16704/0.htm") //热点
                .addUrl("http://new.sousuo.gov.cn/column/16700/0.htm") //部门新闻
                .addUrl("http://new.sousuo.gov.cn/column/16699/0.htm") //地方报道
                .addUrl("http://new.sousuo.gov.cn/column/16697/0.htm") //执法监管
                .addPipeline(jdbcPipeline) // 将抓取到的结果保存到数据库
                .thread(5)
                .run();
    }
}