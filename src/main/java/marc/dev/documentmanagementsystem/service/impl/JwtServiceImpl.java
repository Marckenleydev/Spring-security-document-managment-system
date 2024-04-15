package marc.dev.documentmanagementsystem.service.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import marc.dev.documentmanagementsystem.domain.Token;
import marc.dev.documentmanagementsystem.domain.TokenData;
import marc.dev.documentmanagementsystem.dto.User;
import marc.dev.documentmanagementsystem.enumaration.TokenType;
import marc.dev.documentmanagementsystem.function.TriFunction;
import marc.dev.documentmanagementsystem.security.JwtConfiguration;
import marc.dev.documentmanagementsystem.service.JwtService;
import marc.dev.documentmanagementsystem.service.UserService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.jsonwebtoken.Header.JWT_TYPE;
import static io.jsonwebtoken.Header.TYPE;
import static jakarta.persistence.ValidationMode.NONE;
import static java.time.LocalTime.now;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static marc.dev.documentmanagementsystem.constant.Constants.*;
import static marc.dev.documentmanagementsystem.enumaration.TokenType.ACCESS;
import static marc.dev.documentmanagementsystem.enumaration.TokenType.REFRESH;
import static org.springframework.security.core.authority.AuthorityUtils.commaSeparatedStringToAuthorityList;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtServiceImpl extends JwtConfiguration implements JwtService {
    private final JwtService jwtService;
    private final UserService userService;

    private final Supplier<SecretKey> key = ()-> Keys.hmacShaKeyFor(Decoders.BASE64.decode(getSecret()));

    private final Function<String, Claims> claimsFunction = token ->
            Jwts.parser()
            .verifyWith(key.get())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

    private final Function<String, String> subject = token -> getClaimsValue(token, Claims::getSubject);

    private final BiFunction<HttpServletRequest, String,Optional<String>> extractToken =(request,  cookieName)->
        Optional.of(stream(request.getCookies() == null ? new Cookie[]{ new Cookie(EMPTY_VALUE, EMPTY_VALUE) } : request.getCookies())
                        .filter(cookie -> Objects.equals(cookieName ,cookie.getName()))
                        .map(Cookie::getValue)
                        .findAny())
                .orElse(empty());

    private final BiFunction<HttpServletRequest, String,Optional<Cookie>> extractCookie =  (request, cookieName)->
        Optional.of(stream(request.getCookies() == null ? new Cookie[]{new Cookie(EMPTY_VALUE, EMPTY_VALUE)} : request.getCookies())
                .findAny()).orElse(empty());


    private final Supplier<JwtBuilder> builder = ()->

        Jwts.builder()
                .header().add(Map.of(TYPE, JWT_TYPE))
                .and()
                .audience().add("GET_ARRAYS_LLC")
                .and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .notBefore(new Date())
                .signWith(key.get(), Jwts.SIG.HS512);

    private final BiFunction<User, TokenType , String> buildToken =(user, type) ->
            Objects.equals(type, ACCESS) ? builder.get()
                    .subject(user.getUserId())
                    .claim(AUTHORITIES, user.getAuthorities())
                    .claim(ROLE, user.getRole())
                    .expiration(Date.from(Instant.from(now().plusSeconds(getExpiration()))))
                    .compact() : builder.get()
                    .subject(user.getUserId())
                    .expiration(Date.from(Instant.now().plusSeconds(getExpiration())))
                    .compact();

    private final TriFunction<HttpServletResponse, User, TokenType> addCookie = (response, user, type)->{
        switch (type) {
            case ACCESS -> {
                var accessToken = createToken(user, Token::getAccessToken);
                var cookie = new Cookie(type.getValue(), accessToken);
                cookie.setHttpOnly(true);
//                cookie.setSecure(true);
                cookie.setMaxAge(2* 60);
                cookie.setPath("/");
                cookie.setAttribute("SameSite", NONE.name());
                response.addCookie(cookie);
            }

            case REFRESH -> {
                var refreshToken = createToken(user, Token::getRefreshToken);
                var cookie = new Cookie(type.getValue(), refreshToken);
                cookie.setHttpOnly(true);
//                cookie.setSecure(true);
                cookie.setMaxAge(2 * 60 * 60);
                cookie.setPath("/");
                cookie.setAttribute("SameSite", NONE.name());
                response.addCookie(cookie);
            }
        }
    };

    private <T> T getClaimsValue(String token, Function<Claims, T> claims){
        return claimsFunction.andThen(claims).apply(token);
    }


    public  Function<String, List<GrantedAuthority>> authorities = token->
            commaSeparatedStringToAuthorityList(new StringJoiner(AUTHORITY_DELIMITER)
                    .add(claimsFunction.apply(token).get(AUTHORITIES, String.class))
                    .add(ROLE_PREFIX + claimsFunction.apply(token).get(ROLE, String.class)).toString());

    @Override
    public String createToken(User user, Function<Token, String> tokenFunction) {
        var token = Token.builder().accessToken(buildToken.apply(user, ACCESS)).refreshToken(buildToken.apply(user, REFRESH)).build();
        return tokenFunction.apply(token);
    }

    @Override
    public Optional<String> extractToken(HttpServletRequest request, String tokenType) {
        return extractToken.apply(request, tokenType);
    }

    @Override
    public void addCookie(HttpServletResponse response, User user, TokenType type) {
        addCookie.accept(response, user, type);
    }

    @Override
    public <T> T getTokenData(String token, Function<TokenData, T> tokenFunction) {
        return tokenFunction.apply(
                TokenData.builder()
                        .valid(Objects.equals(userService.getUserByUserId(subject.apply(token)).getUserId(), claimsFunction.apply(token).getSubject()))
                        .authorities(authorities.apply(token))
                        .claims(claimsFunction.apply(token))
                        .user(userService.getUserByUserId(subject.apply(token)))
                        .build());
    }

    @Override
    public void removeCookie(HttpServletRequest request, HttpServletResponse response, String cookieName) {
        var optionalCookie = extractCookie.apply(request, cookieName);
        if (optionalCookie.isPresent()){
            var cookie = optionalCookie.get();
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }

    }
}
