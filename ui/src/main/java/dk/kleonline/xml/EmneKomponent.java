package dk.kleonline.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmneKomponent {

    @XmlElement(name = "EmneNr", required = true)
	@JacksonXmlProperty(localName = "EmneNr")
    protected String emneNr;

    @XmlElement(name = "EmneTitel", required = true)
	@JacksonXmlProperty(localName = "EmneTitel")
    protected String emneTitel;
}
