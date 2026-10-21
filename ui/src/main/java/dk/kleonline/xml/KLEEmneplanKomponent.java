package dk.kleonline.xml;

import java.util.List;

import javax.xml.datatype.XMLGregorianCalendar;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JacksonXmlRootElement(localName = "KLE-Emneplan")
public class KLEEmneplanKomponent {

	@XmlElement(name = "UdgivelsesDato", required = true)
	@XmlSchemaType(name = "date")
	@JacksonXmlProperty(localName = "UdgivelsesDato")
    protected XMLGregorianCalendar udgivelsesDato;

	@XmlElement(name = "Hovedgruppe", required = true)
	@JacksonXmlProperty(localName = "Hovedgruppe")
	@JacksonXmlElementWrapper(useWrapping = false)
    protected List<HovedgruppeKomponent> hovedgruppe;
}
