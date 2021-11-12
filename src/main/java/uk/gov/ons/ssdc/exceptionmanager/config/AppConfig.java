package uk.gov.ons.ssdc.exceptionmanager.config;

import java.util.TimeZone;
import javax.annotation.PostConstruct;

import com.godaddy.logging.LoggingConfigs;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {
  @PostConstruct
  public void init() {
    LoggingConfigs.setCurrent(LoggingConfigs.getCurrent().useJson());
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**");
  }
}
