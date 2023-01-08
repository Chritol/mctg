package bif3.tolan.swe1.mcg.workers;

import bif3.tolan.swe1.mcg.constants.GenericHttpResponses;
import bif3.tolan.swe1.mcg.constants.RequestHeaders;
import bif3.tolan.swe1.mcg.constants.RequestPaths;
import bif3.tolan.swe1.mcg.database.respositories.interfaces.CardRepository;
import bif3.tolan.swe1.mcg.database.respositories.interfaces.TradeOfferRepository;
import bif3.tolan.swe1.mcg.database.respositories.interfaces.UserRepository;
import bif3.tolan.swe1.mcg.exceptions.*;
import bif3.tolan.swe1.mcg.httpserver.HttpRequest;
import bif3.tolan.swe1.mcg.httpserver.HttpResponse;
import bif3.tolan.swe1.mcg.httpserver.enums.HttpContentType;
import bif3.tolan.swe1.mcg.httpserver.enums.HttpMethod;
import bif3.tolan.swe1.mcg.httpserver.enums.HttpStatus;
import bif3.tolan.swe1.mcg.model.Card;
import bif3.tolan.swe1.mcg.model.TradeOffer;
import bif3.tolan.swe1.mcg.model.User;
import bif3.tolan.swe1.mcg.utils.TradeUtils;
import bif3.tolan.swe1.mcg.utils.UserUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.Vector;

public class TradeWorker implements Workable {

    private UserRepository userRepository;
    private TradeOfferRepository tradeOfferRepository;
    private CardRepository cardRepository;

    public TradeWorker(UserRepository userRepository, TradeOfferRepository tradeOfferRepository, CardRepository cardRepository) {
        this.userRepository = userRepository;
        this.tradeOfferRepository = tradeOfferRepository;
        this.cardRepository = cardRepository;
    }

    public HttpResponse executeRequest(HttpRequest request) {
        String requestedPath = "";
        HttpMethod httpMethod = request.getMethod();

        if (request.getPathArray().length > 1) {
            requestedPath = request.getPathArray()[1];
        }

        switch (httpMethod) {
            case GET:
                switch (requestedPath) {
                    case RequestPaths.TRADE_WORKER_GET_TRADES:
                        return getAllTradeDeals(request);
                }
            case POST:
                switch (requestedPath) {
                    case RequestPaths.TRADE_WORKER_ADD_TRADE:
                        return createTrade(request);
                    default:
                        return acceptTrade(request, requestedPath);
                }
            case DELETE:
                return deleteTrade(request, requestedPath);
        }

        return GenericHttpResponses.INVALID_PATH;
    }

    private HttpResponse getAllTradeDeals(HttpRequest request) {
        // get username from token
        String authorizationToken = request.getHeaderMap().get(RequestHeaders.AUTH_HEADER);
        String username = UserUtils.extractUsernameFromToken(authorizationToken);

        try {
            userRepository.getUserByUsername(username);

            // get all trade offers
            Vector<TradeOffer> allTradeOffers = tradeOfferRepository.getAllTradeOffersAsList();
            if (allTradeOffers.isEmpty() == false) {
                // load card details for trade offers
                for (TradeOffer tradeOffer : allTradeOffers) {
                    tradeOffer.setCard(cardRepository.getCardByTradeOfferId(tradeOffer.getTradeId()));
                }

                // print all trade offers including the cards
                return new HttpResponse(HttpStatus.OK, HttpContentType.PLAIN_TEXT, TradeUtils.printAllTradeOffers(allTradeOffers));
            } else {
                return GenericHttpResponses.NO_ACTIVE_TRADES;
            }
        } catch (SQLException | UnsupportedCardTypeException | UnsupportedElementTypeException e) {
            e.printStackTrace();
            return GenericHttpResponses.INTERNAL_ERROR;
        } catch (UserDoesNotExistException e) {
            return GenericHttpResponses.INVALID_TOKEN;
        }
    }

    private synchronized HttpResponse acceptTrade(HttpRequest request, String requestedTradeId) {
        // get username from token
        String authorizationToken = request.getHeaderMap().get(RequestHeaders.AUTH_HEADER);
        String username = UserUtils.extractUsernameFromToken(authorizationToken);

        try {
            User requestingUser = userRepository.getUserByUsername(username);

            TradeOffer wantedTrade = tradeOfferRepository.getTradeOfferById(requestedTradeId);
            // check if trade exists
            if (wantedTrade != null) {
                // check to not trade with yourself
                if (wantedTrade.getUserId() != requestingUser.getId()) {
                    // get card offered by user
                    String offerCardIdOfRequestingUser = TradeUtils.extractStringFromJson(request.getBody());
                    Card offerCardOfRequestingUser = cardRepository.getCardById(offerCardIdOfRequestingUser);

                    // check if the card belongs to the user
                    if (cardRepository.doesCardBelongToUser(offerCardOfRequestingUser.getCardId(), requestingUser.getId())) {
                        // check requirements
                        if (TradeUtils.cardMeetsRequirement(offerCardOfRequestingUser, wantedTrade)) {
                            wantedTrade.setCard(cardRepository.getCardByTradeOfferId(wantedTrade.getTradeId()));

                            cardRepository.assignCardToUserStack(wantedTrade.getCard().getCardId(), requestingUser.getId());
                            cardRepository.assignCardToUserStack(offerCardIdOfRequestingUser, wantedTrade.getUserId());

                            tradeOfferRepository.deleteTrade(wantedTrade.getTradeId());

                            return GenericHttpResponses.SUCCESS_TRADE;
                        } else {
                            return GenericHttpResponses.TRADE_CRITERIA_NOT_MET;
                        }
                    } else {
                        return GenericHttpResponses.ITEM_NOT_OWNED;
                    }
                } else {
                    return GenericHttpResponses.IDENTICAL_USER;
                }
            } else {
                return GenericHttpResponses.TRADE_NOT_FOUND;
            }
        } catch (SQLException | UnsupportedElementTypeException | UnsupportedCardTypeException e) {
            e.printStackTrace();
            return GenericHttpResponses.INTERNAL_ERROR;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return GenericHttpResponses.INVALID_INPUT;
        } catch (InvalidInputException e) {
            return GenericHttpResponses.INVALID_INPUT;
        } catch (TradeOfferNotFoundException e) {
            return GenericHttpResponses.TRADE_NOT_FOUND;
        } catch (UserDoesNotExistException e) {
            return GenericHttpResponses.INVALID_TOKEN;
        }
    }

    private synchronized HttpResponse createTrade(HttpRequest request) {
        // get username from token
        String authorizationToken = request.getHeaderMap().get(RequestHeaders.AUTH_HEADER);
        String username = UserUtils.extractUsernameFromToken(authorizationToken);

        try {
            User dbUser = userRepository.getUserByUsername(username);

            // check if user has already a trade
            if (tradeOfferRepository.getTradeOfferByUserId(dbUser.getId()) == null) {
                // create trade offer from jsonString
                ObjectMapper mapper = new ObjectMapper();
                String jsonString = request.getBody();
                TradeOffer tradeOffer = mapper.readValue(jsonString, TradeOffer.class);

                // check if trade with ID exists
                if (tradeOfferRepository.getTradeOfferById(tradeOffer.getTradeId()) == null) {
                    // check if card belongs to user
                    if (cardRepository.doesCardBelongToUser(tradeOffer.getTradeCardId(), dbUser.getId())) {
                        // create trade and remove card from user deck
                        tradeOffer.setUserId(dbUser.getId());
                        tradeOfferRepository.createTradeOffer(tradeOffer);
                        cardRepository.assignCardToTradeOffer(tradeOffer.getTradeCardId(), tradeOffer.getTradeId());
                        return GenericHttpResponses.SUCCESS_CREATE;
                    } else {
                        return GenericHttpResponses.ITEM_NOT_OWNED;
                    }
                } else {
                    return GenericHttpResponses.ID_EXISTS;
                }
            } else {
                return GenericHttpResponses.HAS_ACTIVE_TRADE;
            }
        } catch (SQLException | UnsupportedCardTypeException |
                 UnsupportedElementTypeException e) {
            e.printStackTrace();
            return GenericHttpResponses.INTERNAL_ERROR;
        } catch (InvalidInputException e) {
            return GenericHttpResponses.INVALID_INPUT;
        } catch (HasActiveTradeException e) {
            return GenericHttpResponses.HAS_ACTIVE_TRADE;
        } catch (UserDoesNotExistException e) {
            return GenericHttpResponses.INVALID_TOKEN;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return GenericHttpResponses.INVALID_INPUT;
        }
    }

    private synchronized HttpResponse deleteTrade(HttpRequest request, String requestedTradeId) {
        // get username from token
        String authorizationToken = request.getHeaderMap().get(RequestHeaders.AUTH_HEADER);
        String username = UserUtils.extractUsernameFromToken(authorizationToken);

        try {
            User dbUser = userRepository.getUserByUsername(username);

            // check if trade offer exists
            TradeOffer tradeOffer = tradeOfferRepository.getTradeOfferById(requestedTradeId);
            if (tradeOffer != null) {
                // check if trade belongs to user
                if (tradeOffer.getUserId() == dbUser.getId()) {
                    // delete trade and put card back to user deck
                    tradeOffer.setCard(cardRepository.getCardByTradeOfferId(tradeOffer.getTradeId()));
                    cardRepository.assignCardToUserStack(tradeOffer.getCard().getCardId(), dbUser.getId());
                    tradeOfferRepository.deleteTrade(tradeOffer.getTradeId());
                    return GenericHttpResponses.SUCCESS_DELETE;
                } else {
                    return GenericHttpResponses.ITEM_NOT_OWNED;
                }
            } else {
                return GenericHttpResponses.TRADE_NOT_FOUND;
            }
        } catch (SQLException | UnsupportedCardTypeException | UnsupportedElementTypeException e) {
            e.printStackTrace();
            return GenericHttpResponses.INTERNAL_ERROR;
        } catch (InvalidInputException e) {
            return GenericHttpResponses.INVALID_INPUT;
        } catch (TradeOfferNotFoundException e) {
            return GenericHttpResponses.TRADE_NOT_FOUND;
        } catch (UserDoesNotExistException e) {
            return GenericHttpResponses.INVALID_TOKEN;
        }
    }
}
