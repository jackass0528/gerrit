import org.eclipse.jgit.lib.ObjectId;
  private final String fileName;
  private final String content;
    this.fileName = fileName;
    this.content = content;
      .committer(new PersonIdent(i, testRepo.getClock()));
    commitBuilder.add(fileName, content);
    commitBuilder.rm(fileName);
    return new Result(ref, pushHead(testRepo, ref, tag != null, force), c,
        subject);
    public ObjectId getCommitId() {
    public RevCommit getCommit() {
      return commit;
        throws OrmException {
        throws OrmException {
      Iterable<Account.Id> actualIds =
          approvalsUtil.getReviewers(db, notesFactory.create(c)).values();
        .named(message(refUpdate))