package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.cumuli.boot.actuator.HTTPHealthIndicator;
import be.milieuinfo.antivirus.dienst.scanbestand.v1.MilieuAvdAntivirusWebService;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AntivirusDienstConfiguration {

  @Configuration
  @Profile(Constants.SPRING_PROFILE_PRODUCTION)
  public static class ProductionAntivirusConfiguration {
    @Value("${antivirus.url}")
    private String wsdlLocatie;

    @Bean
    public MilieuAvdAntivirusWebService antivirusService() {
      JaxWsProxyFactoryBean jaxWsProxyFactoryBean = new JaxWsProxyFactoryBean();
      jaxWsProxyFactoryBean.setServiceClass(MilieuAvdAntivirusWebService.class);
      jaxWsProxyFactoryBean.setAddress(wsdlLocatie);
      Map<String, Object> props = new HashMap<>();
      props.put("mtom-enabled", true);
      jaxWsProxyFactoryBean.setProperties(props);
      return (MilieuAvdAntivirusWebService) jaxWsProxyFactoryBean.create();
    }

    @Bean
    public HTTPHealthIndicator avdHealthIndicator(@Value("${antivirus.health-url}") URI url)
        throws MalformedURLException {
      return HTTPHealthIndicator.ofHealthURL(url.toURL());
    }
  }
}
