package es.udc.ws.app.model.ofertaservice;

import static es.udc.ws.app.model.util.DataSourceConstant.OFERTA_DATA_SOURCE;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

import javax.sql.DataSource;

import es.udc.ws.app.exceptions.OfertaEmailException;
import es.udc.ws.app.exceptions.OfertaEstadoException;
import es.udc.ws.app.exceptions.OfertaMaxPersonasException;
import es.udc.ws.app.exceptions.OfertaReclamaDateException;
import es.udc.ws.app.exceptions.OfertaReservaDateException;
import es.udc.ws.app.exceptions.ReservaEstadoException;
import es.udc.ws.app.model.oferta.Oferta;
import es.udc.ws.app.model.oferta.Oferta.Estado;
import es.udc.ws.app.model.oferta.SqlOfertaDao;
import es.udc.ws.app.model.oferta.SqlOfertaDaoFactory;
import es.udc.ws.app.model.reserva.Reserva;
import es.udc.ws.app.model.reserva.SqlReservaDao;
import es.udc.ws.app.model.reserva.SqlReservaDaoFactory;
import es.udc.ws.app.validation.PropertyValidator;
import es.udc.ws.util.exceptions.InputValidationException;
import es.udc.ws.util.exceptions.InstanceNotFoundException;
import es.udc.ws.util.sql.DataSourceLocator;

public class OfertaServiceImpl implements OfertaService {
    /*
     * IMPORTANT: Some JDBC drivers require "setTransactionIsolation" to
     * be called before "setAutoCommit".
     */

    private DataSource dataSource;
    private SqlOfertaDao ofertaDao = null;
    private SqlReservaDao reservaDao = null;

    public OfertaServiceImpl() {
        dataSource = DataSourceLocator.getDataSource(OFERTA_DATA_SOURCE);
        ofertaDao = SqlOfertaDaoFactory.getDao();
        reservaDao = SqlReservaDaoFactory.getDao();
    }

    private void validateOferta(Oferta oferta) throws InputValidationException {

        PropertyValidator.validateMandatoryString("titulo", oferta.getTitulo());
        PropertyValidator.validateMandatoryString("descripcion", oferta.getDescripcion());
        PropertyValidator.validateDouble("precioReal", oferta.getPrecioReal(), 0, Float.MAX_VALUE);
        PropertyValidator.validateDouble("precioRebajado", oferta.getPrecioRebajado(), 0, Float.MAX_VALUE);
        PropertyValidator.validateDate("iniReserva", oferta.getIniReserva());
        PropertyValidator.validateDate("limReserva", oferta.getLimReserva());
        PropertyValidator.validateDate("limOferta", oferta.getLimOferta());
        PropertyValidator.validateLong("maxPersonas", oferta.getMaxPersonas(), 1L, Long.MAX_VALUE);
        // Ponemos un 1 de minimo en el validate de maxPersonas porque una oferta necesita poder reservarse por alguien
        // Si se crea una oferta con la intencion de que aun no pueda reservarse no se pondria maxPersonas = 0
         // sino que se pondria el inicio para comenzar la reserva mas adelante cuando se considere en vez de poner
          // maxPersonas = 0 y luego hacer un update de este
        
        if (oferta.getEstado() == null)
        	throw new InputValidationException("Estado = null");
    }

    @Override
    public Oferta addOferta(Oferta oferta) throws InputValidationException {

        try (Connection connection = dataSource.getConnection()) {
            try {
                /* Prepare connection. */
                connection
                        .setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);

                validateOferta(oferta);

                /* Checks */
                if (oferta.getIniReserva().after(oferta.getLimReserva()))
                	throw new InputValidationException("Invalid reserva dates, start reserva '>' lim reserva");
                
                if (oferta.getLimReserva().after(oferta.getLimOferta()))
                	throw new InputValidationException("Invalid reserva dates, lim reserva '>' lim reclamar oferta");
                
                /* Do work. */
                Oferta createdOferta = ofertaDao.create(connection, oferta);

                /* Commit. */
                connection.commit();

                return createdOferta;

            } catch (SQLException e) {
                connection.rollback();
                throw new RuntimeException(e);
            } catch (RuntimeException | Error e) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void updateOferta(Long ofertaId, String titulo, String descripcion, Calendar iniReserva, Calendar limReserva, Calendar limOferta, float precioReal, float precioRebajado, Long maxPersonas) throws InputValidationException, InstanceNotFoundException, OfertaEstadoException {

        try (Connection connection = dataSource.getConnection()) {
            try {
                /* Prepare connection. */
                connection
                        .setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);

                /* Sets y validation */
                Oferta old = findOferta(ofertaId);
                
                //Menos costoso que recorrer con un for e ir comparando si es distinto o no.
                old.setTitulo(titulo);
                old.setDescripcion(descripcion);
                old.setIniReserva(iniReserva);
                old.setLimReserva(limReserva);
                old.setLimOferta(limOferta);
                old.setPrecioReal(precioReal);
                old.setPrecioRebajado(precioRebajado);
                old.setMaxPersonas(maxPersonas);
                
                validateOferta(old);
                
                /* Checks */
                if (old.getEstado() != Oferta.Estado.CREADA) {
                	throw new OfertaEstadoException(ofertaId, old.getEstado().name());
                }
                
                if (iniReserva.after(limReserva))
                	throw new InputValidationException("Invalid reserva dates, start reserva '>' lim reserva");
                
                if (limReserva.after(limOferta))
                	throw new InputValidationException("Invalid reserva dates, lim reserva '>' lim reclamar oferta");
                
                /* Do work. */
                ofertaDao.update(connection, old);

                /* Commit. */
                connection.commit();

            } catch (InstanceNotFoundException e) {
                connection.commit();
                throw e;
            } catch (SQLException e) {
                connection.rollback();
                throw new RuntimeException(e);
            } catch (RuntimeException | Error e) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void removeOferta(Long ofertaId) throws InstanceNotFoundException, InputValidationException, OfertaEstadoException {
    	
        try (Connection connection = dataSource.getConnection()) {
            try {
                /* Prepare connection. */
                connection
                        .setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);

                /* Checks */
            	Oferta old = findOferta(ofertaId);

                if (old.getEstado() != Oferta.Estado.CREADA && old.getEstado() != Oferta.Estado.LIBERADA) {
                	throw new OfertaEstadoException(old.getOfertaId(), old.getEstado().name());
                }
                
                /* Do work. */
                ofertaDao.remove(connection, ofertaId);

                /* Commit. */
                connection.commit();

            } catch (InstanceNotFoundException e) {
                connection.commit();
                throw e;
            } catch (SQLException e) {
                connection.rollback();
                throw new RuntimeException(e);
            } catch (RuntimeException | Error e) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Oferta findOferta(Long ofertaId) throws InstanceNotFoundException {

        try (Connection connection = dataSource.getConnection()) {
            return ofertaDao.find(connection, ofertaId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

	@Override
	public List<Oferta> findOfertas() {
		return findOfertas(null, null, null);
	}

	@Override
	public List<Oferta> findOfertas(String keywords, Estado estado, Calendar fecha) {
		try (Connection connection = dataSource.getConnection()) {
				return ofertaDao.findByKeywords(connection, keywords, estado, fecha);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
	}

    @Override
    public Long reservarOferta(Long ofertaId, String emailUsuario, String numeroTarjeta)
            throws InstanceNotFoundException, InputValidationException, OfertaMaxPersonasException, OfertaEmailException, OfertaReservaDateException {

    	Long reservaId = null;
    	
	        try (Connection connection = dataSource.getConnection()) {
	
	            try {
	
	                /* Prepare connection. */
	                connection
	                        .setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
	                connection.setAutoCommit(false);
	                
	            	PropertyValidator.validateEmail(emailUsuario);
	                PropertyValidator.validateCreditCard(numeroTarjeta);
	                
	                /* Checks */
	            	Oferta old = findOferta(ofertaId);

	            	if (old.getNumReservas() == old.getMaxPersonas())
	                	throw new OfertaMaxPersonasException(ofertaId, old.getMaxPersonas());

	            	/*if (old.getMaxPersonas()==findReservas(ofertaId, null).size())
	                	throw new OfertaMaxPersonasException(ofertaId, old.getMaxPersonas());*/

	            	for (Reserva reserva : findReservas(ofertaId, null)) { 
	            		if (reserva.getEmailUsuario().equals(emailUsuario))
	                    	throw new OfertaEmailException(ofertaId, emailUsuario);    		
	            	}
	            	
	            	Calendar now = Calendar.getInstance();
	        		if (old.getIniReserva().after(now) || old.getLimReserva().before(now)) {
	                	throw new OfertaReservaDateException(ofertaId);    		
	        		}	
	        		
	        		/* Check state */
	        		
	        		old.setNumReservas(old.getNumReservas() + 1);
	    	        if (old.getEstado() == Oferta.Estado.CREADA || old.getEstado() == Oferta.Estado.LIBERADA) 
	    	        	old.setEstado(Oferta.Estado.COMPROMETIDA);
	    	        
	                /* Do work and update state. */
	                ofertaDao.update(connection, old);
	                reservaId = reservaDao.create(connection, new Reserva(ofertaId, emailUsuario,
	                        numeroTarjeta, Reserva.Estado.PENDIENTE, Calendar.getInstance()));
	
	                /* Commit. */
	                connection.commit();
		
	            } catch (SQLException e) {
	                connection.rollback();
	                throw new RuntimeException(e);
	            } catch (RuntimeException | Error e) {
	                connection.rollback();
	                throw e;
	            }
	
	        } catch (SQLException e) {
	            throw new RuntimeException(e);
	        }
		return reservaId;
    }

	@Override
	public List<Reserva> findReservas(Long ofertaId, Reserva.Estado estado)
			throws InstanceNotFoundException {
        try (Connection connection = dataSource.getConnection()) {

            List<Reserva> reservas = reservaDao.findReservas(connection, ofertaId, estado);
            
            return reservas;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
	}
	
    @Override
    public Reserva findReserva(Long reservaId) 
    		throws InstanceNotFoundException {
        try (Connection connection = dataSource.getConnection()) {

            Reserva reserva = reservaDao.find(connection, reservaId);
            
            return reserva;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

	@Override
	public void reclamarOferta(Long reservaId) throws InstanceNotFoundException, ReservaEstadoException, OfertaReclamaDateException {
		
        try (Connection connection = dataSource.getConnection()) {
            try {
                /* Prepare connection. */
                connection
                        .setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);

                /* Checks */
        		Reserva reserva = findReserva(reservaId);
        		
        		if (reserva.getEstado() == Reserva.Estado.CERRADA)
                	throw new ReservaEstadoException(reservaId, reserva.getEstado().name());    		
        		
        		Oferta oferta = findOferta(reserva.getOfertaId());
        		Calendar now = Calendar.getInstance();
        		if (oferta.getLimOferta().before(now)) {
                	throw new OfertaReclamaDateException(oferta.getOfertaId());    		
        		}	
        		
                /* Do work and update state. */
        		reserva.setEstado(Reserva.Estado.CERRADA);
        		reservaDao.update(connection, reserva);
        		
        		
        		// Si todas las reservas se han disfrutado ==> Estado de la oferta liberada!
        		/*if (findReservas(oferta.getOfertaId(), 
        				Reserva.ESTADO_PENDIENTE).size() == 1) //1 porque aun no hicimos commit del estado de esta reserva
        			oferta.setEstado(Oferta.ESTADO_LIBERADA); */
        		
        		if (oferta.getNumReservas() == oferta.getNumUsedReservas() + 1)
        			oferta.setEstado(Oferta.Estado.LIBERADA);
        		
        		oferta.setNumUsedReservas(oferta.getNumUsedReservas() + 1);
        		
        		ofertaDao.update(connection, oferta);
        		
                /* Commit. */
                connection.commit();
        		        		
            } catch (InstanceNotFoundException e) {
                connection.commit();
                throw e;
            } catch (SQLException e) {
                connection.rollback();
                throw new RuntimeException(e);
            } catch (RuntimeException | Error e) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
		
	}
    
}
