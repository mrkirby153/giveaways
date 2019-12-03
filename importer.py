import json


class ObjectSerializable:

    def _default_handler(self, obj):
        if obj != self and isinstance(obj, ObjectSerializable):
            return obj.to_object()
        else:
            return obj.__dict__

    def to_object(self):
        return json.loads(json.dumps(self, default=self._default_handler,
                                     sort_keys=True, indent=4))


class Category(ObjectSerializable):

    def __init__(self, name, channel, options):
        self.name = name
        self.channel = channel
        self.options = options

    def __repr__(self):
        return str(self.__dict__)


class Option(ObjectSerializable):

    def __init__(self, name, emote, custom):
        self.name = name
        self.emote = emote
        self.custom = custom

    def __repr__(self):
        return str(self.__dict__)


last_channel = ""


def prompt_yes_no(prompt):
    resp = input(f"{prompt} [Y/n] ").lower()
    if resp == "yes" or resp == "y":
        return True
    else:
        return False


def prompt_category():
    global last_channel
    name = input("Category name: ")
    channel = input(f"Category Channel (Blank for last: {last_channel}): ")

    channel = last_channel if channel == "" else channel

    last_channel = channel

    options = []
    print("Inputting options")
    while True:
        options.append(prompt_option())
        if not prompt_yes_no("One More?"):
            break
    return Category(name, channel, options)


def prompt_option():
    name = input("Enter the option name: ")
    emote = input("Enter the option emote: ")
    if emote != "" and prompt_yes_no("Custom"):
        custom = True
    else:
        custom = False
    return Option(name, emote, custom)


categories = []


def write_out():
    out_file = input("\n\nOutput File: ")
    with open(out_file, 'w') as f:
        f.write(json.dumps(list(map(lambda x: x.to_object(), categories)),
                           indent=4))
    print("File has been saved")
    exit(0)


def main():
    global categories
    while True:
        print(":= Category =:")
        categories.append(prompt_category())
        if not prompt_yes_no("One more category?"):
            write_out()


if __name__ == '__main__':
    main()
