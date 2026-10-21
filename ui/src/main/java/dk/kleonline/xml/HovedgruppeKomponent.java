package dk.kleonline.xml;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HovedgruppeKomponent {

    @XmlElement(name = "HovedgruppeNr", required = true)
	@JacksonXmlProperty(localName = "HovedgruppeNr")
    protected String hovedgruppeNr;

    @XmlElement(name = "HovedgruppeTitel", required = true)
	@JacksonXmlProperty(localName = "HovedgruppeTitel")
    protected String hovedgruppeTitel;

    @XmlElement(name = "Gruppe", required = true)
	@JacksonXmlProperty(localName = "Gruppe")
	@JacksonXmlElementWrapper(useWrapping = false)
    protected List<GruppeKomponent> gruppe;

}
