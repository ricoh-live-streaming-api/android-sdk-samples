
public class LoggerFactory
{
    private LoggerFactory() { }

    public static Log GetLogger(string name)
    {
        return new Log(name);
    }
}
