package dk.kleonline.xml;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GruppeKomponent {

    @XmlElement(name = "GruppeNr", required = true)
	@JacksonXmlProperty(localName = "GruppeNr")
    protected String gruppeNr;
    
    @XmlElement(name = "GruppeTitel", required = true)
	@JacksonXmlProperty(localName = "GruppeTitel")
    protected String gruppeTitel;

    @XmlElement(name = "Emne", required = true)
	@JacksonXmlProperty(localName = "Emne")
	@JacksonXmlElementWrapper(useWrapping = false)
    protected List<EmneKomponent> emne;

}
