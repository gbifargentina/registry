package org.gbif.identity.model;

import java.util.Date;

import com.google.common.base.Objects;

/**
 * The sessions open for the user, which could be on multiple devices.
 * This could be part of the public GBIF API models in the future.
 */
public class Session {
  private String userName;
  private String session;
  private Date created;

  public Session() {
  }

  public Session(String userName, String session) {
    this.userName = userName;
    this.session = session;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Session session1 = (Session) o;
    return Objects.equal(userName, session1.userName) &&
           Objects.equal(session, session1.session) &&
           Objects.equal(created, session1.created);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(userName, session, created);
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getSession() {
    return session;
  }

  public void setSession(String session) {
    this.session = session;
  }

  public Date getCreated() {
    return created;
  }

  public void setCreated(Date created) {
    this.created = created;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
                  .add("userName", userName)
                  .add("session", session)
                  .add("created", created)
                  .toString();
  }
}