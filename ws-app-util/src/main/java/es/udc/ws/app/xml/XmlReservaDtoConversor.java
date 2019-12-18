package es.udc.ws.app.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.jdom2.DataConversionException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;

import es.udc.ws.app.dto.ReservaDto;
import es.udc.ws.app.dto.ReservaDto.Estado;

public class XmlReservaDtoConversor {

    public final static Namespace XML_NS = Namespace
            .getNamespace("http://ws.udc.es/reservas/xml");

    public static Document toXml(ReservaDto reserva)
            throws IOException {

        Element reservaElement = toJDOMElement(reserva);

        return new Document(reservaElement);
    }

	public static Document toXml(List<ReservaDto> reservas)
        throws IOException {

        Element reservasElement = new Element("reservas", XML_NS);
        for (int i = 0; i < reservas.size(); i++) {
            ReservaDto xmlReservaDto = reservas.get(i);
            Element reservaElement = toJDOMElement(xmlReservaDto);
            reservasElement.addContent(reservaElement);
        }

        return new Document(reservasElement);
	}
    
    public static Element toJDOMElement(ReservaDto reserva) {
        Element reservaElement = new Element("reserva", XML_NS);

        if (reserva.getReservaId() != null) {
            Element reservaIdElement = new Element("reservaId", XML_NS);
            reservaIdElement.setText(reserva.getReservaId().toString());
            reservaElement.addContent(reservaIdElement);
        }

        if (reserva.getOfertaId() != null) {
            Element ofertaIdElement = new Element("ofertaId", XML_NS);
            ofertaIdElement.setText(reserva.getOfertaId().toString());
            reservaElement.addContent(ofertaIdElement);
        }
        
        Element estadoElement = new Element("estado", XML_NS);
        estadoElement.setText(reserva.getEstado().name());
        reservaElement.addContent(estadoElement);
        
        if (reserva.getFechaReserva() != null) {
            Element fechaReservaElement = getFecha(reserva
                    .getFechaReserva(), "fechaReserva");
            reservaElement.addContent(fechaReservaElement);
        }

        return reservaElement;
    }
    public static ReservaDto toReserva(InputStream reservaXml)
            throws ParsingException {
        try {

            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(reservaXml);
            Element rootElement = document.getRootElement();

            return toReserva(rootElement);
        } catch (ParsingException ex) {
            throw ex;
        } catch (Exception e) {
            throw new ParsingException(e);
        }
    }
    
    private static ReservaDto toReserva(Element reservaElement)
            throws ParsingException, DataConversionException,
            NumberFormatException {
        if (!"reserva".equals(reservaElement.getName())) {
            throw new ParsingException("Unrecognized element '"
                    + reservaElement.getName() + "' ('reserva' expected)");
        }
        Element reservaIdElement = reservaElement.getChild("reservaId", XML_NS);
        Long reservaId = null;
        if (reservaIdElement != null) {
            reservaId = Long.valueOf(reservaIdElement.getTextTrim());
        }

        Element ofertaIdElement = reservaElement.getChild("ofertaId", XML_NS);
        Long ofertaId = null;
        if (ofertaIdElement != null) {
            ofertaId = Long.valueOf(ofertaIdElement.getTextTrim());
        }

        Estado estado = Estado.valueOf(reservaElement.getChildTextTrim("estado", XML_NS));
        
        Calendar fechaReserva = getFecha(reservaElement.getChild(
                "fechaReserva", XML_NS));

        return new ReservaDto(reservaId, ofertaId, estado, fechaReserva);
    }

    public static List<ReservaDto> toReservas(InputStream reservaXml)
            throws ParsingException {
        try {

            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(reservaXml);
            Element rootElement = document.getRootElement();

            if(!"reservas".equalsIgnoreCase(rootElement.getName())) {
                throw new ParsingException("Unrecognized element '"
                    + rootElement.getName() + "' ('reservas' expected)");
            }
            @SuppressWarnings("unchecked")
			List<Element> children = rootElement.getChildren();
            List<ReservaDto> reservaDtos = new ArrayList<>(children.size());
            for (int i = 0; i < children.size(); i++) {
                Element element = children.get(i);
                reservaDtos.add(toReserva(element));
            }

            return reservaDtos;
        } catch (ParsingException ex) {
            throw ex;
        } catch (Exception e) {
            throw new ParsingException(e);
        }
    }
    
    private static Calendar getFecha(Element fechaElement)
            throws DataConversionException {

        if (fechaElement == null) {
            return null;
        }
        int second = fechaElement.getAttribute("second").getIntValue();
        int minute = fechaElement.getAttribute("minute").getIntValue();
        int hour = fechaElement.getAttribute("hour").getIntValue();
        int day = fechaElement.getAttribute("day").getIntValue();
        int month = fechaElement.getAttribute("month").getIntValue();
        int year = fechaElement.getAttribute("year").getIntValue();
        Calendar releaseDate = Calendar.getInstance();

        releaseDate.set(Calendar.SECOND, second);
        releaseDate.set(Calendar.MINUTE, minute);
        releaseDate.set(Calendar.HOUR_OF_DAY, hour);
        releaseDate.set(Calendar.DAY_OF_MONTH, day);
        releaseDate.set(Calendar.MONTH, month);
        releaseDate.set(Calendar.YEAR, year);

        return releaseDate;

    }

    private static Element getFecha(Calendar fecha, String name) {

        Element releaseDateElement = new Element(name, XML_NS);

        int second = fecha.get(Calendar.SECOND);
        int minute = fecha.get(Calendar.MINUTE);
        int hour = fecha.get(Calendar.HOUR_OF_DAY);
        int day = fecha.get(Calendar.DAY_OF_MONTH);
        int month = fecha.get(Calendar.MONTH);
        int year = fecha.get(Calendar.YEAR);

        releaseDateElement.setAttribute("second", Integer.toString(second));
        releaseDateElement.setAttribute("minute", Integer.toString(minute));
        releaseDateElement.setAttribute("hour", Integer.toString(hour));
        releaseDateElement.setAttribute("day", Integer.toString(day));
        releaseDateElement.setAttribute("month", Integer.toString(month));
        releaseDateElement.setAttribute("year", Integer.toString(year));

        return releaseDateElement;

    }

}
