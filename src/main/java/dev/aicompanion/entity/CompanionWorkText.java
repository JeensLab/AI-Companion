package dev.aicompanion.entity;

/** Player-facing descriptions of the current job and its stage. */
final class CompanionWorkText {
    private CompanionWorkText() { }

    static String taskDescription(CompanionWork work) {
        if (work.job == null) return "";
        String stage = work.phase == null ? "подготовка" : switch (work.phase) {
            case CRAFT -> "крафт";
            case PICKAXE, ORE_PICK, SHOVEL -> "подготовка инструмента";
            case WOOD, WOOD_DONE -> "заготовка дерева";
            case MINE, DIGGING, ORE, OBSIDIAN, BLOCK, BREAK -> "добыча";
            case STORE, CLOSE, PACK, SORT -> "работа с вещами";
            case BUILD, PEN, PORTAL, COBBLE, GOLEM -> "строительство";
            case RETURN, EXIT -> "возвращение";
            case FIGHT, CHASE, DROPS -> "поиск и бой";
            case HAND -> "передача вещей";
            case FURNACE -> "подготовка печи";
            case COOK -> "плавка / готовка";
            case IRON -> "подготовка железа";
            case BREED -> "разведение животных";
            case SCOUT -> "обход торговцев";
            case TRADE -> "обмен с торговцем";
            case FARM -> "в поле";
            case SURVEY -> "осматривается";
            case GUIDE, COURSE -> "в пути";
            case WATER -> "за водой";
            case PICKUP -> "подбор";
            case PLACE -> work.placeHome ? "в пути домой" : "в пути к метке";
            case GRIND -> "у точила";
            case COMPOST -> "у компостера";
            case CHORE -> "по хозяйству";
            case ERRAND -> "поход в Незер";
            case PET -> work.activePet.stageText();
        };
        return "Выполняет " + jobName(work) + " · " + stage;
    }
    /** The work in words for the journal of deeds. */
    static String jobName(CompanionWork work) {
        String item = work.requestedItem.isEmpty() || work.requestedItem.equals("all") || work.requestedItem.equals("logs") ? "" : " «" + CompanionWork.itemName(work.requestedItem) + "»";
        return switch (work.job) {
            case STONE -> "добычу камня (" + work.gathered + " из " + work.wantedStone + ")";
            case CRAFT -> "крафт" + item;
            case STORE -> "складывание вещей в сундук";
            case TAKE -> "взятие вещей из сундука";
            case SORT -> "раскладку по сундукам";
            case PLACE_CHEST -> "установку" + item;
            case HOUSE -> "стройку дома";
            case PEN -> "постройку загона";
            case BREED -> "разведение животных";
            case SCOUT -> "обход торговцев";
            case TRADE -> "обмен с торговцем";
            case DIRT -> "добычу " + work.ground.genitive();
            case COME -> "путь к тебе";
            case HUNT -> "бой с монстрами";
            case GAME -> "охоту (" + work.gameKills + " из " + work.gameWanted + ")";
            case WOOD -> "рубку дерева";
            case GIVE -> "передачу вещей";
            case SMELT -> "плавку" + (work.smelt == null ? "" : " «" + CompanionWork.itemName(work.smelt.input()) + "»");
            case ORE -> "добычу руды «" + work.ore.oreName + "» (" + work.oreMined + " из " + work.oreWanted + ")";
            case FARM -> work.farming.jobName();
            case SURVEY -> "осмотр местности";
            case GUIDE -> "путь к знакомому месту";
            case COURSE -> work.course.jobName();
            case WATER -> "набор воды в ведро";
            case OBSIDIAN -> work.obsidian.jobName();
            case BLOCK -> work.picking.jobName();
            case BREAK -> work.breakBlock.jobName();
            case PORTAL -> work.endPortal.active() ? work.endPortal.jobName() : work.portal.jobName();
            case COBBLE_GEN -> work.cobbleGen.jobName();
            case GOLEM -> work.golem.jobName();
            case PICK_UP -> "подбор" + (work.pickUp == null ? " вещей" : " «" + work.pickUp.name() + "»");
            case PLACE -> work.placeHome ? "поход домой" : "поход к метке «" + work.placeName + "»";
            case GRIND -> work.grinding.jobName();
            case COMPOST -> work.composting.jobName();
            case CHORE -> work.chores.jobName();
            case ERRAND -> work.owner.errand.jobName();
            case PET -> "поручение о " + work.activePet.kind.about;
        };
    }
}
